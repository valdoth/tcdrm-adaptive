"""
PLSA Optimisé pour TCDRM - Version rapide avec cache

Cette version optimisée:
- Cache les prédictions de popularité
- Ne réentraîne que tous les N steps (refit_interval)
- Utilise une approximation simple quand pas assez de données
"""

import numpy as np
from typing import Optional


class PLSAPopularityModel:
    """
    Modèle PLSA optimisé pour prédire la popularité avec cache.
    """
    
    def __init__(self, n_topics: int = 3, max_iterations: int = 10, 
                 convergence_threshold: float = 1e-2, seed: Optional[int] = None,
                 refit_interval: int = 200):
        """
        Args:
            n_topics: Nombre de topics latents (3 par défaut)
            max_iterations: Itérations EM réduites à 10 (au lieu de 20)
            convergence_threshold: Seuil de convergence relaxé (1e-2 au lieu de 1e-3)
            seed: Seed pour reproductibilité
            refit_interval: Réentraîner tous les N accès (200 par défaut)
        """
        self.n_topics = n_topics
        self.max_iterations = max_iterations
        self.convergence_threshold = convergence_threshold
        self.refit_interval = refit_interval
        
        self.rng = np.random.RandomState(seed)
        
        # Historique simplifié
        self.access_history = []
        self.window_size = 30  # Réduit de 50 à 30
        
        # Cache pour optimisation
        self.last_fit_step = 0
        self.cached_popularity = 0.5
        self.is_fitted = False
        
        # Paramètres PLSA
        self.P_z_given_d = None
        self.P_w_given_z = None
        self.n_words = 5
        
        # Initialisation
        self._initialize_parameters()
    
    def _initialize_parameters(self):
        """Initialise les paramètres"""
        self.P_w_given_z = self.rng.dirichlet(np.ones(self.n_words), size=self.n_topics)
    
    def add_access(self, access_count: int):
        """Ajoute un accès à l'historique"""
        self.access_history.append(access_count)
        
        # Limiter la taille de l'historique pour économiser mémoire
        if len(self.access_history) > 1000:
            self.access_history = self.access_history[-500:]
    
    def predict_popularity(self) -> float:
        """
        Prédit la popularité avec cache intelligent.
        
        OPTIMISATION: Ne réentraîne que tous les refit_interval steps
        """
        # Pas assez de données
        if len(self.access_history) < 10:
            return 0.1
        
        # Vérifier si on doit réentraîner
        current_step = len(self.access_history)
        steps_since_last_fit = current_step - self.last_fit_step
        should_refit = steps_since_last_fit >= self.refit_interval
        
        # Réentraîner uniquement si nécessaire
        if should_refit or not self.is_fitted:
            self._fast_fit()
            self.last_fit_step = current_step
            self.is_fitted = True
        
        # Retourner la valeur cachée
        return self.cached_popularity
    
    def _fast_fit(self):
        """
        Entraînement rapide avec approximation simple.
        """
        if len(self.access_history) < self.window_size:
            # Approximation simple basée sur la moyenne
            recent = self.access_history[-20:]
            if len(recent) > 0:
                avg = np.mean(recent)
                # Normaliser entre 0.1 et 0.9
                self.cached_popularity = float(np.clip(0.1 + (avg / 1000.0) * 0.8, 0.1, 0.9))
            else:
                self.cached_popularity = 0.5
            return
        
        # Créer matrice document-terme simplifiée
        doc_term_matrix = self._create_simple_matrix()
        
        if doc_term_matrix.shape[0] == 0:
            self.cached_popularity = 0.5
            return
        
        n_documents = doc_term_matrix.shape[0]
        
        # Initialiser P(z|d) si nécessaire
        if self.P_z_given_d is None or self.P_z_given_d.shape[0] != n_documents:
            self.P_z_given_d = self.rng.dirichlet(np.ones(self.n_topics), size=n_documents)
        
        # EM simplifié (moins d'itérations)
        for _ in range(min(5, self.max_iterations)):  # Max 5 itérations
            try:
                self._simple_em_step(doc_term_matrix)
            except:
                # En cas d'erreur, utiliser approximation simple
                break
        
        # Calculer popularité
        if self.P_z_given_d is not None and len(self.P_z_given_d) > 0:
            topic_weights = self.P_z_given_d[-1, :]
            topic_scores = np.linspace(0.2, 0.9, self.n_topics)
            self.cached_popularity = float(np.clip(np.dot(topic_weights, topic_scores), 0.1, 0.9))
        else:
            # Fallback
            recent = self.access_history[-20:]
            if len(recent) > 0:
                avg = np.mean(recent)
                self.cached_popularity = float(np.clip(0.1 + (avg / 1000.0) * 0.8, 0.1, 0.9))
            else:
                self.cached_popularity = 0.5
    
    def _create_simple_matrix(self):
        """Crée une matrice document-terme simplifiée"""
        # Utiliser seulement les derniers accès
        recent_history = self.access_history[-min(len(self.access_history), 200):]
        
        n_windows = max(1, len(recent_history) // self.window_size)
        doc_term_matrix = np.zeros((n_windows, self.n_words))
        
        for i in range(n_windows):
            start_idx = i * self.window_size
            end_idx = min(start_idx + self.window_size, len(recent_history))
            window = recent_history[start_idx:end_idx]
            
            for access in window:
                word_idx = self._discretize_access(access)
                doc_term_matrix[i, word_idx] += 1
        
        return doc_term_matrix
    
    def _discretize_access(self, access_count: int) -> int:
        """Convertit un accès en catégorie"""
        if access_count < 10:
            return 0
        elif access_count < 50:
            return 1
        elif access_count < 150:
            return 2
        elif access_count < 300:
            return 3
        else:
            return 4
    
    def _simple_em_step(self, doc_term_matrix):
        """Étape EM simplifiée"""
        n_documents, n_words = doc_term_matrix.shape
        
        # E-step simplifié
        P_z_given_dw = np.zeros((n_documents, n_words, self.n_topics))
        
        for d in range(n_documents):
            for w in range(n_words):
                if doc_term_matrix[d, w] > 0:
                    for z in range(self.n_topics):
                        P_z_given_dw[d, w, z] = self.P_z_given_d[d, z] * self.P_w_given_z[z, w]
                    
                    # Normaliser
                    total = P_z_given_dw[d, w, :].sum()
                    if total > 0:
                        P_z_given_dw[d, w, :] /= total
        
        # M-step simplifié
        # Mise à jour P(w|z)
        for z in range(self.n_topics):
            for w in range(n_words):
                numerator = 0.0
                denominator = 0.0
                
                for d in range(n_documents):
                    count = doc_term_matrix[d, w]
                    numerator += count * P_z_given_dw[d, w, z]
                    denominator += count * P_z_given_dw[d, :, z].sum()
                
                if denominator > 0:
                    self.P_w_given_z[z, w] = numerator / denominator
                else:
                    self.P_w_given_z[z, w] = 1.0 / n_words
        
        # Mise à jour P(z|d)
        for d in range(n_documents):
            for z in range(self.n_topics):
                total = 0.0
                for w in range(n_words):
                    count = doc_term_matrix[d, w]
                    total += count * P_z_given_dw[d, w, z]
                
                doc_total = doc_term_matrix[d, :].sum()
                if doc_total > 0:
                    self.P_z_given_d[d, z] = total / doc_total
                else:
                    self.P_z_given_d[d, z] = 1.0 / self.n_topics
    
    def reset(self, seed: Optional[int] = None):
        """Réinitialise le modèle"""
        if seed is not None:
            self.rng = np.random.RandomState(seed)
        
        self.access_history = []
        self.last_fit_step = 0
        self.cached_popularity = 0.5
        self.is_fitted = False
        self.P_z_given_d = None
        self._initialize_parameters()
    
    def fit(self):
        """Alias pour compatibilité"""
        self._fast_fit()
    
    def get_topic_distribution(self) -> Optional[np.ndarray]:
        """Retourne la distribution des topics"""
        if self.P_z_given_d is not None and len(self.P_z_given_d) > 0:
            return self.P_z_given_d[-1, :]
        return None
