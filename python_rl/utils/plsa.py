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
    
    def __init__(self, n_topics: int = 3, max_iterations: int = 30, convergence_threshold: float = 1e-4, seed: Optional[int] = None, refit_interval: int = 100):
        """
        Initialise le modèle PLSA amélioré
        
        Args:
            n_topics: Nombre de topics latents (patterns d'accès)
                     - Topic 0: Accès faible/sporadique
                     - Topic 1: Accès moyen/stable
                     - Topic 2: Accès élevé/burst
            max_iterations: Nombre maximum d'itérations EM (augmenté à 30)
            convergence_threshold: Seuil de convergence pour l'algorithme EM (plus strict)
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
        self.is_fitted = False
        self.window_size = 50     # Taille de la fenêtre temporelle
        self.n_words = 5          # Types d'accès: [très faible, faible, moyen, élevé, très élevé]
        
        # Nouvelles métriques pour amélioration
        self.convergence_history = []  # Historique de convergence EM
        self.burst_detector = []       # Détecteur de bursts
        self.trend_window = 20         # Fenêtre pour détection de tendance
        self.adaptive_learning = True  # Learning rate adaptatif pour EM
        
        # Initialisation aléatoire des paramètres
        self._initialize_parameters()
    
    def _initialize_parameters(self):
        """Initialise les paramètres du modèle de manière intelligente"""
        # P(z): Probabilités uniformes pour les topics
        self.P_z = np.ones(self.n_topics) / self.n_topics
        
        # P(w|z): Initialisation améliorée avec biais vers patterns typiques
        # Topic 0 (faible): biais vers mots 0-1
        # Topic 1 (moyen): biais vers mots 1-3
        # Topic 2 (élevé): biais vers mots 3-4
        self.P_w_given_z = np.zeros((self.n_topics, self.n_words))
        
        if self.n_topics == 3:
            # Initialisation informée pour 3 topics
            self.P_w_given_z[0] = [0.4, 0.3, 0.2, 0.07, 0.03]  # Faible
            self.P_w_given_z[1] = [0.1, 0.25, 0.3, 0.25, 0.1]  # Moyen
            self.P_w_given_z[2] = [0.03, 0.07, 0.2, 0.3, 0.4]  # Élevé
        else:
            # Fallback: initialisation aléatoire
            self.P_w_given_z = self.rng.dirichlet(np.ones(self.n_words), size=self.n_topics)
        
        # Ajouter du bruit pour éviter les minima locaux
        noise = self.rng.dirichlet(np.ones(self.n_words), size=self.n_topics) * 0.1
        self.P_w_given_z = self.P_w_given_z * 0.9 + noise
        
        # Normaliser
        self.P_w_given_z = self.P_w_given_z / self.P_w_given_z.sum(axis=1, keepdims=True)
        
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
    
    def _em_step(self, doc_term_matrix: np.ndarray, iteration: int = 0) -> float:
        """
        Effectue une itération optimisée de l'algorithme EM avec learning rate adaptatif
        
        Args:
            doc_term_matrix: Matrice document-terme [n_documents x n_words]
            iteration: Numéro de l'itération (pour learning rate adaptatif)
            
        Returns:
            Log-vraisemblance du modèle
        """
        n_documents, n_words = doc_term_matrix.shape
        
        # Learning rate adaptatif: décroît avec les itérations
        if self.adaptive_learning:
            learning_rate = 1.0 / (1.0 + 0.05 * iteration)
        else:
            learning_rate = 1.0
        
        # E-step: Calculer P(z|d,w) - Vectorisé pour performance
        P_z_given_dw = np.zeros((n_documents, n_words, self.n_topics))
        
        for d in range(n_documents):
            for w in range(n_words):
                if doc_term_matrix[d, w] > 1e-10:
                    # P(z|d,w) ∝ P(w|z) * P(z|d)
                    numerator = self.P_w_given_z[:, w] * self.P_z_given_d[d, :]
                    denominator = numerator.sum()
                    if denominator > 1e-10:
                        P_z_given_dw[d, w, :] = numerator / denominator
        
        # M-step: Mettre à jour les paramètres avec learning rate adaptatif
        # Sauvegarder les anciennes valeurs
        old_P_w_given_z = self.P_w_given_z.copy()
        old_P_z_given_d = self.P_z_given_d.copy()
        
        # Update P(w|z) - Optimisé
        new_P_w_given_z = np.zeros_like(self.P_w_given_z)
        for z in range(self.n_topics):
            for w in range(n_words):
                numerator = 0
                denominator = 0
                for d in range(n_documents):
                    count = doc_term_matrix[d, w] * self.window_size
                    numerator += count * P_z_given_dw[d, w, z]
                    denominator += count * P_z_given_dw[d, :, z].sum()
                
                if denominator > 1e-10:
                    new_P_w_given_z[z, w] = numerator / denominator
                else:
                    new_P_w_given_z[z, w] = old_P_w_given_z[z, w]
        
        # Normaliser P(w|z)
        row_sums = new_P_w_given_z.sum(axis=1, keepdims=True)
        row_sums[row_sums == 0] = 1
        new_P_w_given_z = new_P_w_given_z / row_sums
        
        # Appliquer learning rate adaptatif
        self.P_w_given_z = old_P_w_given_z * (1 - learning_rate) + new_P_w_given_z * learning_rate
        
        # Update P(z|d) - Optimisé
        new_P_z_given_d = np.zeros_like(self.P_z_given_d)
        for d in range(n_documents):
            for z in range(self.n_topics):
                numerator = 0
                for w in range(n_words):
                    count = doc_term_matrix[d, w] * self.window_size
                    numerator += count * P_z_given_dw[d, w, z]
                new_P_z_given_d[d, z] = numerator / self.window_size
        
        # Normaliser P(z|d)
        row_sums = new_P_z_given_d.sum(axis=1, keepdims=True)
        row_sums[row_sums == 0] = 1
        new_P_z_given_d = new_P_z_given_d / row_sums
        
        # Appliquer learning rate adaptatif
        self.P_z_given_d = old_P_z_given_d * (1 - learning_rate) + new_P_z_given_d * learning_rate
        
        # Update P(z) avec pondération temporelle (plus de poids aux documents récents)
        weights = np.exp(np.linspace(-0.5, 0, n_documents))
        weights = weights / weights.sum()
        self.P_z = np.average(self.P_z_given_d, axis=0, weights=weights)
        
        # Calculer la log-vraisemblance
        log_likelihood = 0
        for d in range(n_documents):
            for w in range(n_words):
                if doc_term_matrix[d, w] > 1e-10:
                    p_w_given_d = (self.P_w_given_z[:, w] * self.P_z_given_d[d, :]).sum()
                    if p_w_given_d > 1e-10:
                        log_likelihood += doc_term_matrix[d, w] * np.log(p_w_given_d)
        
        return log_likelihood
    
    def fit(self):
        """
        Entraîne le modèle PLSA sur l'historique des accès avec convergence adaptative
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
            
        # Algorithme EM avec convergence adaptative
        prev_log_likelihood = -np.inf
        self.convergence_history = []
        patience = 5  # Nombre d'itérations sans amélioration avant arrêt
        no_improvement_count = 0
        best_log_likelihood = -np.inf
            
        for iteration in range(self.max_iterations):
            log_likelihood = self._em_step(doc_term_matrix, iteration)
            self.convergence_history.append(log_likelihood)
                
            # Vérifier la convergence avec critère adaptatif
            improvement = log_likelihood - prev_log_likelihood
            
            if improvement > 0:
                best_log_likelihood = log_likelihood
                no_improvement_count = 0
            else:
                no_improvement_count += 1
            
            # Arrêt si convergence ou pas d'amélioration
            if abs(improvement) < self.convergence_threshold:
                break
            
            # Early stopping si pas d'amélioration pendant patience itérations
            if no_improvement_count >= patience:
                break
                
            prev_log_likelihood = log_likelihood
    
    def _detect_burst(self) -> float:
        """Détecte les bursts d'accès récents"""
        if len(self.access_history) < 10:
            return 0.0
        
        recent = self.access_history[-10:]
        older = self.access_history[-30:-10] if len(self.access_history) >= 30 else self.access_history[:-10]
        
        if len(older) == 0:
            return 0.0
        
        recent_avg = np.mean(recent)
        older_avg = np.mean(older)
        
        if older_avg < 0.1:
            return 0.0
        
        # Ratio d'augmentation
        burst_ratio = (recent_avg - older_avg) / (older_avg + 1e-6)
        return float(np.clip(burst_ratio, 0.0, 1.0))
    
    def _detect_trend(self) -> float:
        """Détecte la tendance (croissante/décroissante) des accès"""
        if len(self.access_history) < self.trend_window:
            return 0.0
        
        recent = self.access_history[-self.trend_window:]
        x = np.arange(len(recent))
        
        # Régression linéaire simple
        mean_x = np.mean(x)
        mean_y = np.mean(recent)
        
        numerator = np.sum((x - mean_x) * (recent - mean_y))
        denominator = np.sum((x - mean_x) ** 2)
        
        if denominator < 1e-6:
            return 0.0
        
        slope = numerator / denominator
        # Normaliser la pente
        return float(np.clip(slope / 0.5, -1.0, 1.0))
    
    def predict_popularity(self) -> float:
        """
        Prédit la popularité actuelle basée sur les patterns d'accès
        AMÉLIORÉ avec:
        - Détection de bursts
        - Détection de tendances
        - Pondération adaptative PLSA/récent basée sur confiance
        - Meilleure gestion des cas limites
            
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
        
        # Calculer les différentes composantes
        plsa_score = 0.5
        recent_score = 0.5
        burst_score = self._detect_burst()
        trend_score = self._detect_trend()
        
        # Score PLSA si disponible
        if self.P_z_given_d is not None and len(self.P_z_given_d) > 0:
            # Utiliser la distribution des topics du dernier document
            topic_weights = self.P_z_given_d[-1, :]
            # Scores des topics: 0=faible, 1=moyen, 2=élevé
            topic_scores = np.array([0.2, 0.5, 0.9])
            plsa_score = np.dot(topic_weights, topic_scores)
        
        # Score basé sur accès récents avec pondération exponentielle
        recent_window = min(30, len(self.access_history))
        recent_accesses = self.access_history[-recent_window:]
        weights = np.exp(np.linspace(-1, 0, len(recent_accesses)))
        weights /= weights.sum()
        weighted_avg = np.average(recent_accesses, weights=weights)
        recent_score = weighted_avg / 4.0  # Normaliser [0-4] → [0-1]
        
        # Pondération adaptative basée sur la confiance du modèle
        # Plus de données = plus de confiance dans PLSA
        data_confidence = min(1.0, len(self.access_history) / 200.0)
        plsa_weight = 0.5 + 0.3 * data_confidence  # 0.5 à 0.8
        recent_weight = 1.0 - plsa_weight
        
        # Combiner les scores
        base_popularity = plsa_weight * plsa_score + recent_weight * recent_score
        
        # Ajuster avec burst et tendance
        # Burst augmente la popularité
        base_popularity += burst_score * 0.15
        
        # Tendance positive augmente, négative diminue
        if trend_score > 0:
            base_popularity += trend_score * 0.1
        else:
            base_popularity += trend_score * 0.05  # Moins d'impact négatif
        
        # Clipper et cacher
        self.cached_popularity = float(np.clip(base_popularity, 0.0, 1.0))
        
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
