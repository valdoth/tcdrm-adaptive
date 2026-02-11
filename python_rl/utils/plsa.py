"""
PLSA (Probabilistic Latent Semantic Analysis) pour modéliser la popularité des données

Implémentation basée sur:
- Hofmann, T. (1999). "Probabilistic latent semantic analysis"
- Adapté pour les patterns d'accès aux données dans le cloud

Le modèle PLSA identifie des topics latents dans les patterns d'accès
et prédit la probabilité d'accès futur basée sur l'historique.
"""

import numpy as np
from typing import List, Tuple, Optional
from collections import defaultdict


class PLSAPopularityModel:
    """
    Modèle PLSA pour prédire la popularité des données basée sur les patterns d'accès
    
    Le modèle apprend:
    - P(z|d): Probabilité d'un topic z pour un document/requête d
    - P(w|z): Probabilité d'un mot/accès w pour un topic z
    
    Pour notre cas:
    - Documents (d) = fenêtres temporelles de requêtes
    - Mots (w) = types d'accès (lecture, écriture, etc.)
    - Topics (z) = patterns d'accès latents (haute fréquence, burst, stable, etc.)
    """
    
    def __init__(self, n_topics: int = 3, max_iterations: int = 20, convergence_threshold: float = 1e-3, seed: Optional[int] = None, refit_interval: int = 100):
        """
        Initialise le modèle PLSA
        
        Args:
            n_topics: Nombre de topics latents (patterns d'accès)
                     - Topic 0: Accès faible/sporadique
                     - Topic 1: Accès moyen/stable
                     - Topic 2: Accès élevé/burst
            max_iterations: Nombre maximum d'itérations EM
            convergence_threshold: Seuil de convergence pour l'algorithme EM
            seed: Seed pour le générateur aléatoire (reproductibilité)
            refit_interval: Intervalle entre les réentraînements (pour optimisation)
        """
        self.n_topics = n_topics
        self.max_iterations = max_iterations
        self.convergence_threshold = convergence_threshold
        self.refit_interval = refit_interval
        
        # Générateur aléatoire pour reproductibilité
        self.rng = np.random.RandomState(seed)
        
        # Paramètres du modèle
        self.P_z_given_d = None  # P(z|d): [n_documents x n_topics]
        self.P_w_given_z = None  # P(w|z): [n_topics x n_words]
        self.P_z = None          # P(z): [n_topics]
        
        # Historique des accès
        self.access_history = []
        
        # Cache pour optimisation
        self.last_fit_step = 0
        self.cached_popularity = 0.5
        self.is_fitted = False  # Liste des accès récents
        self.window_size = 50     # Taille de la fenêtre temporelle
        self.n_words = 5          # Types d'accès: [très faible, faible, moyen, élevé, très élevé]
        
        # Initialisation aléatoire des paramètres
        self._initialize_parameters()
    
    def _initialize_parameters(self):
        """Initialise les paramètres du modèle de manière aléatoire"""
        # P(z): Probabilités uniformes pour les topics
        self.P_z = np.ones(self.n_topics) / self.n_topics
        
        # P(w|z): Probabilités aléatoires normalisées (utiliser rng pour reproductibilité)
        self.P_w_given_z = self.rng.dirichlet(np.ones(self.n_words), size=self.n_topics)
        
        # P(z|d): Sera calculé lors de l'entraînement
        self.P_z_given_d = None
    
    def _discretize_access_count(self, access_count: int) -> int:
        """
        Convertit un compteur d'accès en catégorie discrète
        
        Args:
            access_count: Nombre d'accès dans la fenêtre
            
        Returns:
            Catégorie d'accès (0-4)
        """
        if access_count < 10:
            return 0  # Très faible
        elif access_count < 50:
            return 1  # Faible
        elif access_count < 150:
            return 2  # Moyen
        elif access_count < 300:
            return 3  # Élevé
        else:
            return 4  # Très élevé
    
    def add_access(self, access_count: int):
        """
        Ajoute un nouvel accès à l'historique
        
        Args:
            access_count: Nombre d'accès à cette étape
        """
        word = self._discretize_access_count(access_count)
        self.access_history.append(word)
        
        # Garder seulement les N derniers accès
        if len(self.access_history) > self.window_size * 2:
            self.access_history = self.access_history[-self.window_size * 2:]
    
    def _create_document_term_matrix(self) -> np.ndarray:
        """
        Crée la matrice document-terme à partir de l'historique
        
        Returns:
            Matrice [n_documents x n_words] où chaque document est une fenêtre temporelle
        """
        if len(self.access_history) < self.window_size:
            # Pas assez de données, retourner une matrice uniforme
            return np.ones((1, self.n_words)) / self.n_words
        
        # Créer des documents (fenêtres glissantes)
        n_documents = len(self.access_history) - self.window_size + 1
        doc_term_matrix = np.zeros((n_documents, self.n_words))
        
        for i in range(n_documents):
            window = self.access_history[i:i + self.window_size]
            for word in window:
                doc_term_matrix[i, word] += 1
        
        # Normaliser par document
        row_sums = doc_term_matrix.sum(axis=1, keepdims=True)
        row_sums[row_sums == 0] = 1  # Éviter division par zéro
        doc_term_matrix = doc_term_matrix / row_sums
        
        return doc_term_matrix
    
    def _em_step(self, doc_term_matrix: np.ndarray) -> float:
        """
        Effectue une itération de l'algorithme EM
        
        Args:
            doc_term_matrix: Matrice document-terme [n_documents x n_words]
            
        Returns:
            Log-vraisemblance du modèle
        """
        n_documents, n_words = doc_term_matrix.shape
        
        # E-step: Calculer P(z|d,w)
        P_z_given_dw = np.zeros((n_documents, n_words, self.n_topics))
        
        for d in range(n_documents):
            for w in range(n_words):
                if doc_term_matrix[d, w] > 0:
                    # P(z|d,w) ∝ P(w|z) * P(z|d)
                    numerator = self.P_w_given_z[:, w] * self.P_z_given_d[d, :]
                    denominator = numerator.sum()
                    if denominator > 0:
                        P_z_given_dw[d, w, :] = numerator / denominator
        
        # M-step: Mettre à jour les paramètres
        # Update P(w|z)
        for z in range(self.n_topics):
            for w in range(n_words):
                numerator = 0
                denominator = 0
                for d in range(n_documents):
                    count = doc_term_matrix[d, w] * self.window_size
                    numerator += count * P_z_given_dw[d, w, z]
                    denominator += count * P_z_given_dw[d, :, z].sum()
                
                if denominator > 0:
                    self.P_w_given_z[z, w] = numerator / denominator
        
        # Normaliser P(w|z)
        row_sums = self.P_w_given_z.sum(axis=1, keepdims=True)
        row_sums[row_sums == 0] = 1
        self.P_w_given_z = self.P_w_given_z / row_sums
        
        # Update P(z|d)
        for d in range(n_documents):
            for z in range(self.n_topics):
                numerator = 0
                for w in range(n_words):
                    count = doc_term_matrix[d, w] * self.window_size
                    numerator += count * P_z_given_dw[d, w, z]
                self.P_z_given_d[d, z] = numerator / self.window_size
        
        # Normaliser P(z|d)
        row_sums = self.P_z_given_d.sum(axis=1, keepdims=True)
        row_sums[row_sums == 0] = 1
        self.P_z_given_d = self.P_z_given_d / row_sums
        
        # Update P(z)
        self.P_z = self.P_z_given_d.mean(axis=0)
        
        # Calculer la log-vraisemblance
        log_likelihood = 0
        for d in range(n_documents):
            for w in range(n_words):
                if doc_term_matrix[d, w] > 0:
                    p_w_given_d = (self.P_w_given_z[:, w] * self.P_z_given_d[d, :]).sum()
                    if p_w_given_d > 0:
                        log_likelihood += doc_term_matrix[d, w] * np.log(p_w_given_d)
        
        return log_likelihood
    
    def fit(self):
        """
        Entraîne le modèle PLSA sur l'historique des accès
        """
        if len(self.access_history) < self.window_size:
            # Pas assez de données pour entraîner
            return
            
        # Créer la matrice document-terme
        doc_term_matrix = self._create_document_term_matrix()
        n_documents = doc_term_matrix.shape[0]
            
        # Initialiser P(z|d) si nécessaire (utiliser rng pour reproductibilité)
        if self.P_z_given_d is None or self.P_z_given_d.shape[0] != n_documents:
            self.P_z_given_d = self.rng.dirichlet(np.ones(self.n_topics), size=n_documents)
            
        # Algorithme EM
        prev_log_likelihood = -np.inf
            
        for iteration in range(self.max_iterations):
            log_likelihood = self._em_step(doc_term_matrix)
                
            # Vérifier la convergence
            if abs(log_likelihood - prev_log_likelihood) < self.convergence_threshold:
                break
                
            prev_log_likelihood = log_likelihood
    
    def predict_popularity(self) -> float:
        """
        Prédit la popularité actuelle basée sur les patterns d'accès
        OPTIMISÉ: Ne réentraîne que tous les refit_interval steps
        Amélioré: Utilise une pondération exponentielle pour les accès récents
            
        Returns:
            Score de popularité entre 0 et 1
        """
        if len(self.access_history) < 10:
            # Pas assez de données, retourner une popularité faible
            return 0.1
            
        # Vérifier si on doit réentraîner
        current_step = len(self.access_history)
        should_refit = (current_step - self.last_fit_step) >= self.refit_interval
            
        # Entraîner le modèle uniquement si nécessaire
        if should_refit or not self.is_fitted:
            self.fit()
            self.last_fit_step = current_step
            self.is_fitted = True
                
            # Calculer et cacher la nouvelle popularité
            if self.P_z_given_d is not None and len(self.P_z_given_d) > 0:
                # Utiliser la distribution des topics du dernier document
                topic_weights = self.P_z_given_d[-1, :]
                # Scores des topics: 0=faible, 1=moyen, 2=élevé
                topic_scores = np.array([0.2, 0.5, 0.9])
                
                # Pondération exponentielle des accès récents (plus de poids aux récents)
                recent_window = min(30, len(self.access_history))
                recent_accesses = self.access_history[-recent_window:]
                weights = np.exp(np.linspace(-1, 0, len(recent_accesses)))
                weights /= weights.sum()
                weighted_avg = np.average(recent_accesses, weights=weights)
                
                # Combiner PLSA et moyenne pondérée (70% PLSA, 30% moyenne)
                plsa_score = np.dot(topic_weights, topic_scores)
                recent_score = weighted_avg / 4.0  # Normaliser [0-4] → [0-1]
                self.cached_popularity = float(np.clip(0.7 * plsa_score + 0.3 * recent_score, 0.0, 1.0))
            else:
                # Fallback: utiliser la moyenne pondérée des accès récents
                recent_window = min(20, len(self.access_history))
                recent_accesses = self.access_history[-recent_window:]
                weights = np.exp(np.linspace(-1, 0, len(recent_accesses)))
                weights /= weights.sum()
                weighted_avg = np.average(recent_accesses, weights=weights)
                self.cached_popularity = float(np.clip(weighted_avg / 4.0, 0.0, 1.0))
        
        return self.cached_popularity
    
    def get_topic_distribution(self) -> Optional[np.ndarray]:
        """
        Retourne la distribution des topics pour le dernier document
            
        Returns:
            Distribution des topics [n_topics] ou None si pas de données
        """
        if self.P_z_given_d is not None and len(self.P_z_given_d) > 0:
            return self.P_z_given_d[-1, :]
        return None
    
    def reset(self, seed: Optional[int] = None):
        """Réinitialise le modèle"""
        if seed is not None:
            self.rng = np.random.RandomState(seed)
            
        self.P_z_given_d = None
        self.P_w_given_z = None
        self.access_history = []
        self.last_fit_step = 0
        self.cached_popularity = 0.5
        self.is_fitted = False
        self._initialize_parameters()
