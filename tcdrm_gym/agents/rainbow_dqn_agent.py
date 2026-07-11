"""
Rainbow DQN Agent — Hessel et al. 2018

6 composants intégrés :
  1. Double DQN            : online-net choisit l'action, target-net l'évalue
  2. Dueling Networks      : flux Value + Advantage séparés
  3. Prioritized Replay    : échantillonnage proportionnel à |δ|^α, poids IS
  4. N-step Returns        : retour multi-pas G = Σ γ^i r_{t+i}
  5. Noisy Networks        : bruit factoriel appris (Fortunato et al. 2017)
  6. Distributional C51    : opérateur de Bellman projeté sur support fixe
"""

import math
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from collections import deque
from typing import Tuple


# ---------------------------------------------------------------------------
# Utilitaires
# ---------------------------------------------------------------------------

class RunningMeanStd:
    """Algorithme de Welford — normalisation en ligne des récompenses."""
    def __init__(self, epsilon: float = 1e-8):
        self.mean  = np.float64(0.0)
        self.var   = np.float64(1.0)
        self.count = np.float64(epsilon)

    def update(self, x: float):
        x = np.float64(x)
        self.count += 1.0
        delta      = x - self.mean
        self.mean += delta / self.count
        self.var  += (delta * (x - self.mean) - self.var) / self.count

    def normalize(self, x: float) -> float:
        return float(np.clip((x - self.mean) / (np.sqrt(self.var) + 1e-8), -10.0, 10.0))


class NStepBuffer:
    """
    Accumulateur de transitions n-step — Rainbow composant 4.
    G_n = r_t + γ r_{t+1} + … + γ^{n-1} r_{t+n-1}
    """
    def __init__(self, n_step: int, gamma: float):
        self.n_step = n_step
        self.gamma  = gamma
        self._buf: deque = deque()

    def push(self, state, action, reward, next_state, done) -> list:
        self._buf.append((state, action, reward, next_state, done))
        ready = []
        if len(self._buf) >= self.n_step:
            ready.append(self._make_transition())
            self._buf.popleft()
        if done:
            while self._buf:
                ready.append(self._make_transition())
                self._buf.popleft()
        return ready

    def _make_transition(self):
        s0, a0 = self._buf[0][0], self._buf[0][1]
        G, ns, d = 0.0, None, False
        for i, (_, _, r, ns_i, d_i) in enumerate(self._buf):
            G  += (self.gamma ** i) * r
            ns, d = ns_i, d_i
            if d_i:
                break
        return s0, a0, G, ns, d

    def clear(self):
        self._buf.clear()


# ---------------------------------------------------------------------------
# NoisyLinear — Rainbow composant 5 (Fortunato et al. 2017)
# Bruit factoriel : seules 2×n valeurs aléatoires génèrent n² bruits de poids
# ---------------------------------------------------------------------------

class NoisyLinear(nn.Module):
    """
    Couche linéaire avec bruit paramétrique appris.

    Initialisation selon Fortunato eq. 9 :
      μ  ~ U[-1/√p, 1/√p]
      σ  = σ₀/√p   (p = in_features, pour poids ET biais)
    """
    def __init__(self, in_features: int, out_features: int, sigma_init: float = 0.5):
        super().__init__()
        self.in_features  = in_features
        self.out_features = out_features

        self.weight_mu    = nn.Parameter(torch.empty(out_features, in_features))
        self.weight_sigma = nn.Parameter(torch.empty(out_features, in_features))
        self.bias_mu      = nn.Parameter(torch.empty(out_features))
        self.bias_sigma   = nn.Parameter(torch.empty(out_features))
        self.register_buffer('weight_epsilon', torch.empty(out_features, in_features))
        self.register_buffer('bias_epsilon',   torch.empty(out_features))

        mu_range  = 1.0 / math.sqrt(in_features)
        sigma_val = sigma_init / math.sqrt(in_features)   # Fortunato eq. 9 : σ₀/√p pour tous
        self.weight_mu.data.uniform_(-mu_range, mu_range)
        self.weight_sigma.data.fill_(sigma_val)
        self.bias_mu.data.uniform_(-mu_range, mu_range)
        self.bias_sigma.data.fill_(sigma_val)              # FIX : sqrt(p) aussi pour le biais
        self.reset_noise()

    @staticmethod
    def _f(x: torch.Tensor) -> torch.Tensor:
        return x.sign() * x.abs().sqrt()

    def reset_noise(self):
        eps_p = self._f(torch.randn(self.in_features,  device=self.weight_mu.device))
        eps_q = self._f(torch.randn(self.out_features, device=self.weight_mu.device))
        self.weight_epsilon.copy_(eps_q.outer(eps_p))
        self.bias_epsilon.copy_(eps_q)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        if self.training:
            w = self.weight_mu + self.weight_sigma * self.weight_epsilon
            b = self.bias_mu   + self.bias_sigma   * self.bias_epsilon
        else:
            w, b = self.weight_mu, self.bias_mu
        return F.linear(x, w, b)


# ---------------------------------------------------------------------------
# Réseaux neuronaux
# ---------------------------------------------------------------------------

class NoisyDuelingNetwork(nn.Module):
    """Dueling + NoisyLinear sans C51 (5/6 composants)."""
    def __init__(self, state_dim: int, action_dim: int,
                 hidden_dims: list = None, sigma_init: float = 0.5):
        super().__init__()
        if hidden_dims is None:
            hidden_dims = [128, 128]
        self.features = nn.Sequential(
            nn.Linear(state_dim, hidden_dims[0]), nn.ReLU(),
            nn.Linear(hidden_dims[0], hidden_dims[1]), nn.ReLU(),
        )
        self.value_h   = NoisyLinear(hidden_dims[-1], 64, sigma_init)
        self.value_out = NoisyLinear(64, 1,            sigma_init)
        self.adv_h     = NoisyLinear(hidden_dims[-1], 64, sigma_init)
        self.adv_out   = NoisyLinear(64, action_dim,   sigma_init)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        f = self.features(x)
        v = self.value_out(F.relu(self.value_h(f)))
        a = self.adv_out(F.relu(self.adv_h(f)))
        return v + (a - a.mean(dim=1, keepdim=True))

    def reset_noise(self):
        for m in self.modules():
            if isinstance(m, NoisyLinear):
                m.reset_noise()


class C51NoisyDuelingNetwork(nn.Module):
    """
    Rainbow complet (6/6) : C51 + Dueling + NoisyLinear.

    Modélise la distribution des retours sur n_atoms atomes ∈ [v_min, v_max].
    Q(s,a) = E[Z(s,a)] = Σᵢ zᵢ · pᵢ(s,a)
    """
    def __init__(self, state_dim: int, action_dim: int,
                 hidden_dims: list = None,
                 n_atoms: int = 51, v_min: float = -10.0, v_max: float = 10.0,
                 sigma_init: float = 0.5):
        super().__init__()
        if hidden_dims is None:
            hidden_dims = [128, 128]
        self.action_dim = action_dim
        self.n_atoms    = n_atoms
        self.v_min      = v_min
        self.v_max      = v_max
        self.delta_z    = (v_max - v_min) / (n_atoms - 1)
        self.register_buffer('support', torch.linspace(v_min, v_max, n_atoms))

        self.features = nn.Sequential(
            nn.Linear(state_dim, hidden_dims[0]), nn.ReLU(),
            nn.Linear(hidden_dims[0], hidden_dims[1]), nn.ReLU(),
        )
        self.value_h   = NoisyLinear(hidden_dims[-1], 64, sigma_init)
        self.value_out = NoisyLinear(64, n_atoms,         sigma_init)
        self.adv_h     = NoisyLinear(hidden_dims[-1], 64, sigma_init)
        self.adv_out   = NoisyLinear(64, action_dim * n_atoms, sigma_init)

    def _logits(self, x: torch.Tensor) -> torch.Tensor:
        """Logits dueling avant softmax — (batch, action_dim, n_atoms)."""
        B = x.size(0)
        f = self.features(x)
        v = self.value_out(F.relu(self.value_h(f))).view(B, 1, self.n_atoms)
        a = self.adv_out(F.relu(self.adv_h(f))).view(B, self.action_dim, self.n_atoms)
        return v + (a - a.mean(dim=1, keepdim=True))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """Distributions softmax (B, action_dim, n_atoms) — utilisé pour l'inférence."""
        return F.softmax(self._logits(x), dim=2)

    def forward_log(self, x: torch.Tensor) -> torch.Tensor:
        """Log-distributions (B, action_dim, n_atoms) — numériquement stable pour la perte C51."""
        return F.log_softmax(self._logits(x), dim=2)

    def q_values(self, x: torch.Tensor) -> torch.Tensor:
        """Q-valeurs espérées : Σᵢ zᵢ · pᵢ(s,a) — (batch, action_dim)."""
        return (self.forward(x) * self.support.unsqueeze(0).unsqueeze(0)).sum(dim=2)

    def reset_noise(self):
        for m in self.modules():
            if isinstance(m, NoisyLinear):
                m.reset_noise()


# ---------------------------------------------------------------------------
# Prioritized Experience Replay — Rainbow composant 3
# ---------------------------------------------------------------------------

class PrioritizedReplayBuffer:
    """
    Replay buffer proportionnel — P(i) ∝ |δᵢ|^α.
    Poids d'importance sampling : wᵢ = (N · P(i))^{-β}, normalisés par max.
    β anneal de beta_start → 1 sur beta_frames transitions.
    """
    def __init__(self, capacity: int = 100000, alpha: float = 0.6,
                 beta_start: float = 0.4, beta_frames: int = 100000):
        self.capacity    = capacity
        self.alpha       = alpha
        self.beta_start  = beta_start
        self.beta_frames = beta_frames
        self.frame       = 1
        self.buffer: list = []
        self.priorities  = np.zeros(capacity, dtype=np.float64)
        self.position    = 0

    def push(self, state, action, reward, next_state, done):
        max_p = float(self.priorities[:len(self.buffer)].max()) if self.buffer else 1.0
        if len(self.buffer) < self.capacity:
            self.buffer.append((state, action, reward, next_state, done))
        else:
            self.buffer[self.position] = (state, action, reward, next_state, done)
        self.priorities[self.position] = max_p
        self.position = (self.position + 1) % self.capacity

    def sample(self, batch_size: int) -> Tuple:
        n     = len(self.buffer)
        prios = self.priorities[:n]
        probs = prios ** self.alpha
        probs = probs / probs.sum()

        indices = np.random.choice(n, batch_size, p=probs, replace=False)
        beta    = min(1.0, self.beta_start + self.frame * (1.0 - self.beta_start) / self.beta_frames)
        self.frame += 1

        weights = (n * probs[indices]) ** (-beta)
        weights = (weights / weights.max()).astype(np.float32)

        batch = [self.buffer[i] for i in indices]
        s, a, r, ns, d = zip(*batch)
        return (np.array(s, dtype=np.float32), np.array(a, dtype=np.int64),
                np.array(r, dtype=np.float32), np.array(ns, dtype=np.float32),
                np.array(d, dtype=np.float32), indices, weights)

    def update_priorities(self, indices, priorities):
        for idx, p in zip(indices, priorities):
            self.priorities[idx] = max(float(p), 1e-8)

    def __len__(self):
        return len(self.buffer)


# ---------------------------------------------------------------------------
# Agent Rainbow DQN
# ---------------------------------------------------------------------------

class RainbowDQNAgent:
    """
    Rainbow DQN — intègre les 6 améliorations de Hessel et al. 2018.

    use_distributional=True  (défaut) → C51 complet (6/6)
    use_distributional=False          → Noisy Dueling DQN (5/6, sans C51)
    """
    def __init__(
        self,
        state_dim:          int   = 9,
        action_dim:         int   = 3,
        hidden_dims:        list  = None,
        learning_rate:      float = 0.0001,
        discount_factor:    float = 0.99,
        buffer_capacity:    int   = 100000,
        batch_size:         int   = 32,
        tau:                float = 0.005,
        gradient_clip:      float = 10.0,
        device:             str   = None,
        lr_scheduler:       str   = 'cosine',
        scheduler_params:   dict  = None,
        n_step:             int   = 3,
        min_buffer_size:    int   = 500,
        normalize_rewards:  bool  = True,
        sigma_init:         float = 0.5,
        use_distributional: bool  = True,
        n_atoms:            int   = 51,
        v_min:              float = -10.0,
        v_max:              float = 10.0,
    ):
        if hidden_dims is None:
            hidden_dims = [128, 128]

        self.state_dim          = state_dim
        self.action_dim         = action_dim
        self.discount_factor    = discount_factor
        self.batch_size         = batch_size
        self.tau                = tau
        self.gradient_clip      = gradient_clip
        self.n_step             = max(1, n_step)
        self._min_buffer_size   = max(batch_size, min_buffer_size)
        self.normalize_rewards  = normalize_rewards
        self.use_distributional = use_distributional
        self.n_atoms            = n_atoms
        self.v_min              = v_min
        self.v_max              = v_max
        self.update_count       = 0
        self.training_steps     = 0
        self.episode_rewards: list = []
        self.losses: list          = []

        self.device = (torch.device('cuda' if torch.cuda.is_available() else 'cpu')
                       if device is None else torch.device(device))

        def _make_net():
            if use_distributional:
                return C51NoisyDuelingNetwork(
                    state_dim, action_dim, hidden_dims, n_atoms, v_min, v_max, sigma_init)
            return NoisyDuelingNetwork(state_dim, action_dim, hidden_dims, sigma_init)

        self.policy_net = _make_net().to(self.device)
        self.target_net = _make_net().to(self.device)
        self.target_net.load_state_dict(self.policy_net.state_dict())
        self.target_net.eval()   # target toujours en mode déterministe (pas de bruit actif)

        self.optimizer = optim.Adam(
            self.policy_net.parameters(),
            lr=learning_rate, eps=1.5e-4, weight_decay=1e-5)
        self._init_scheduler(lr_scheduler, scheduler_params or {})

        self.replay_buffer = PrioritizedReplayBuffer(
            buffer_capacity, beta_frames=max(buffer_capacity, 100000))
        self._nstep_buf    = NStepBuffer(self.n_step, self.discount_factor)
        # Normalisation de récompense séparée par régime (simple=0, complex=1) : les échelles
        # naturelles diffèrent (CSLA_COMPLEX ≈ 2.7× CSLA_SIMPLE, coûts/latences plus élevés en
        # complex), donc un normalisateur unique partagé dilue le signal d'apprentissage de
        # chaque régime dans les statistiques de l'autre.
        self._reward_stats = {0: RunningMeanStd(), 1: RunningMeanStd()} if normalize_rewards else None

    # -----------------------------------------------------------------------
    # Scheduler
    # -----------------------------------------------------------------------

    def _init_scheduler(self, name: str, params: dict):
        self.scheduler = None
        if name == 'cosine':
            self.scheduler = optim.lr_scheduler.CosineAnnealingLR(
                self.optimizer,
                T_max=int(params.get('T_max', 20000)),
                eta_min=float(params.get('eta_min', 1e-6)))
        elif name == 'step':
            self.scheduler = optim.lr_scheduler.StepLR(
                self.optimizer,
                step_size=int(params.get('step_size', 1000)),
                gamma=float(params.get('gamma', 0.9)))

    # -----------------------------------------------------------------------
    # Sélection d'action — Rainbow composant 5 (NoisyNet exploration)
    # -----------------------------------------------------------------------

    def select_action(self, state: np.ndarray, training: bool = True,
                      action_mask: np.ndarray = None) -> int:
        if action_mask is None:
            action_mask = np.ones(self.action_dim, dtype=np.float32)
        if not np.any(action_mask > 0):
            return 0

        # NoisyNet : rééchantillonner le bruit avant chaque inférence en mode training
        # Sans ce reset, le bruit est fixe depuis __init__ → politique déterministe
        if training:
            self.policy_net.reset_noise()

        with torch.no_grad():
            s = torch.FloatTensor(state).unsqueeze(0).to(self.device)
            if self.use_distributional:
                q = self.policy_net.q_values(s).cpu().numpy()[0]
            else:
                q = self.policy_net(s).cpu().numpy()[0]
            q = q.copy()
            q[action_mask == 0] = -np.inf
            return int(q.argmax())

    # -----------------------------------------------------------------------
    # Mise à jour — stockage + apprentissage
    # -----------------------------------------------------------------------

    def update(self, state, action, reward, next_state, done):
        if self._reward_stats is not None:
            regime = 1 if len(state) > 8 and state[8] >= 0.5 else 0
            stats = self._reward_stats[regime]
            stats.update(reward)
            reward = stats.normalize(reward)

        for transition in self._nstep_buf.push(state, action, reward, next_state, done):
            self.replay_buffer.push(*transition)
        if done:
            self._nstep_buf.clear()

        if len(self.replay_buffer) >= self._min_buffer_size:
            self._train_step()

    # -----------------------------------------------------------------------
    # Étape d'entraînement
    # -----------------------------------------------------------------------

    def _train_step(self):
        s, a, r, ns, d, indices, weights = self.replay_buffer.sample(self.batch_size)
        s  = torch.FloatTensor(s).to(self.device)
        a  = torch.LongTensor(a).to(self.device)
        r  = torch.FloatTensor(r).to(self.device)
        ns = torch.FloatTensor(ns).to(self.device)
        d  = torch.FloatTensor(d).to(self.device)
        w  = torch.FloatTensor(weights).to(self.device)

        # Rééchantillonner le bruit du réseau online seulement (target est en eval())
        self.policy_net.reset_noise()

        if self.use_distributional:
            loss, td_err = self._c51_loss(s, a, r, ns, d, w)
        else:
            loss, td_err = self._noisy_dqn_loss(s, a, r, ns, d, w)

        self.optimizer.zero_grad()
        loss.backward()
        nn.utils.clip_grad_norm_(self.policy_net.parameters(), self.gradient_clip)
        self.optimizer.step()
        if self.scheduler is not None:
            self.scheduler.step()

        self.replay_buffer.update_priorities(indices, td_err + 1e-6)
        self.losses.append(float(loss.item()))
        self.update_count   += 1
        self.training_steps += 1

        # Double DQN composant 1 : soft update du réseau cible (Polyak averaging)
        with torch.no_grad():
            for tp, pp in zip(self.target_net.parameters(), self.policy_net.parameters()):
                tp.data.mul_(1.0 - self.tau).add_(self.tau * pp.data)

    # -----------------------------------------------------------------------
    # Perte Noisy DQN (composants 1-5, sans C51)
    # -----------------------------------------------------------------------

    def _noisy_dqn_loss(self, s, a, r, ns, d, w):
        """Double DQN Huber loss avec PER weights."""
        curr_q = self.policy_net(s).gather(1, a.unsqueeze(1)).squeeze(1)
        with torch.no_grad():
            # Double DQN : online sélectionne, target évalue
            next_acts = self.policy_net(ns).argmax(1)
            next_q    = self.target_net(ns).gather(1, next_acts.unsqueeze(1)).squeeze(1)
            target_q  = r + (1.0 - d) * (self.discount_factor ** self.n_step) * next_q
        td_err = (curr_q - target_q).abs().detach().cpu().numpy()
        loss   = (w * F.smooth_l1_loss(curr_q, target_q, reduction='none')).mean()
        return loss, td_err

    # -----------------------------------------------------------------------
    # Perte C51 — Rainbow composant 6 (Bellemare et al. 2017)
    # -----------------------------------------------------------------------

    def _c51_loss(self, s, a, r, ns, d, w):
        """
        Opérateur de Bellman catégoriel projeté.

        1. Calculer la distribution cible : T̂Z(s', a*) projeté sur le support
        2. Minimiser KL(m || p_θ(s, a)) via cross-entropie
        3. Retourner l'erreur TD scalaire pour la mise à jour des priorités PER
        """
        B       = s.size(0)
        gamma_n = self.discount_factor ** self.n_step
        support = self.policy_net.support          # (n_atoms,)
        v_min   = self.policy_net.v_min
        v_max   = self.policy_net.v_max
        delta_z = self.policy_net.delta_z
        n_atoms = self.policy_net.n_atoms

        with torch.no_grad():
            # Double DQN : online net sélectionne l'action
            next_acts = self.policy_net.q_values(ns).argmax(1)          # (B,)

            # Target net évalue la distribution de la meilleure action
            next_dist = self.target_net(ns)[range(B), next_acts]        # (B, n_atoms)

            # Projection de Bellman : T̂z_j = r + γⁿ · z_j, clamped dans [v_min, v_max]
            Tz = r.unsqueeze(1) + (1.0 - d.unsqueeze(1)) * gamma_n * support.unsqueeze(0)
            Tz = Tz.clamp(v_min, v_max)                                 # (B, n_atoms)

            # Indices flottants des atomes projetés
            b = (Tz - v_min) / delta_z                                  # (B, n_atoms)
            l = b.floor().long().clamp(0, n_atoms - 1)
            u = b.ceil().long().clamp(0, n_atoms - 1)

            # FIX : quand l==u (b est exactement un entier), u-b = b-l = 0
            # → la masse disparaîtrait sans correction.
            # On ajoute 1 à la contribution inférieure dans ce cas.
            same  = (l == u).float()
            l_frac = (u.float() - b) + same   # = 1 si l==u, sinon (u-b)
            u_frac = (b - l.float()) * (1.0 - same)  # = 0 si l==u, sinon (b-l)

            # Projection des masses sur le support discret (scatter)
            m      = torch.zeros(B, n_atoms, device=self.device)
            offset = torch.arange(B, device=self.device).unsqueeze(1) * n_atoms
            m.view(-1).index_add_(0, (l + offset).view(-1),
                                  (next_dist * l_frac).view(-1))
            m.view(-1).index_add_(0, (u + offset).view(-1),
                                  (next_dist * u_frac).view(-1))

        # Cross-entropie KL(m || p_θ(s, a)) — log_softmax pour stabilité numérique
        # FIX : utiliser forward_log() au lieu de forward().log() pour éviter log(~0)
        log_p = self.policy_net.forward_log(s)[range(B), a]             # (B, n_atoms)
        loss  = (w * -(m * log_p).sum(1)).mean()

        # Erreur TD scalaire pour PER (différence de Q-valeurs espérées)
        with torch.no_grad():
            q_online = self.policy_net.q_values(s).gather(1, a.unsqueeze(1)).squeeze(1)
            q_target = (self.target_net(ns) * support.unsqueeze(0).unsqueeze(0)).sum(2)
            q_target_a = q_target[range(B), next_acts]
            td_target  = r + (1.0 - d) * gamma_n * q_target_a
            td_err     = (q_online - td_target).abs().cpu().numpy()

        return loss, td_err

    # -----------------------------------------------------------------------
    # Utilitaires
    # -----------------------------------------------------------------------

    def decay_epsilon(self):
        pass  # NoisyLinear gère l'exploration — pas d'ε-greedy

    def get_network_stats(self) -> dict:
        grad_norm = sum(
            p.grad.norm().item() for p in self.policy_net.parameters() if p.grad is not None)
        sigmas = [m.weight_sigma.abs().mean().item()
                  for m in self.policy_net.modules() if isinstance(m, NoisyLinear)]
        stats = {
            'mode':             'rainbow' if self.use_distributional else 'noisy_dqn',
            'policy_params':    sum(p.numel() for p in self.policy_net.parameters()),
            'policy_grad_norm': grad_norm,
            'buffer_size':      len(self.replay_buffer),
            'update_count':     self.update_count,
            'avg_loss':         float(np.mean(self.losses[-100:])) if self.losses else 0.0,
        }
        if self._reward_stats is not None:
            stats['reward_mean_simple']   = float(self._reward_stats[0].mean)
            stats['reward_std_simple']    = float(np.sqrt(max(self._reward_stats[0].var, 0)))
            stats['reward_mean_complex']  = float(self._reward_stats[1].mean)
            stats['reward_std_complex']   = float(np.sqrt(max(self._reward_stats[1].var, 0)))
        if sigmas:
            stats['avg_noisy_sigma'] = float(np.mean(sigmas))
        return stats

    def save(self, path: str):
        torch.save({
            'policy_net_state_dict': self.policy_net.state_dict(),
            'target_net_state_dict': self.target_net.state_dict(),
            'optimizer_state_dict':  self.optimizer.state_dict(),
            'state_dim':         self.state_dim,
            'update_count':      self.update_count,
            'training_steps':    self.training_steps,
            'episode_rewards':   self.episode_rewards,
            'losses':            self.losses,
            'use_distributional': self.use_distributional,
            'n_atoms':           self.n_atoms,
            'v_min':             self.v_min,
            'v_max':             self.v_max,
            'n_step':            self.n_step,
            'reward_stats': {
                regime: {
                    'mean':  float(rs.mean),
                    'var':   float(rs.var),
                    'count': float(rs.count),
                } for regime, rs in self._reward_stats.items()
            } if self._reward_stats is not None else None,
        }, path)

    def load(self, path: str):
        ckpt = torch.load(path, map_location=self.device, weights_only=False)

        # Détecter l'incompatibilité de state_dim (ex. ancien checkpoint 7D vs 8D courant)
        try:
            policy_sd = ckpt['policy_net_state_dict']
            # Utilise state_dim sauvegardé si disponible, sinon infère depuis les poids
            ckpt_state_dim = ckpt.get('state_dim')
            if ckpt_state_dim is None:
                first_key = next(k for k in policy_sd if 'features.0.weight' in k)
                ckpt_state_dim = policy_sd[first_key].shape[1]
            if ckpt_state_dim != self.state_dim:
                print(
                    f'[RainbowDQN] WARNING: checkpoint state_dim={ckpt_state_dim} '
                    f'!= current state_dim={self.state_dim}. '
                    f'Discarding incompatible checkpoint — starting fresh.')
                return
        except Exception:
            pass

        self.policy_net.load_state_dict(ckpt['policy_net_state_dict'])
        self.target_net.load_state_dict(ckpt['target_net_state_dict'])
        self.optimizer.load_state_dict(ckpt['optimizer_state_dict'])
        self.update_count       = ckpt.get('update_count', 0)
        self.training_steps     = ckpt.get('training_steps', 0)
        self.episode_rewards    = ckpt.get('episode_rewards', [])
        self.losses             = ckpt.get('losses', [])
        self.use_distributional = ckpt.get('use_distributional', True)
        self.n_atoms            = ckpt.get('n_atoms', 51)
        self.v_min              = ckpt.get('v_min', -10.0)
        self.v_max              = ckpt.get('v_max', 10.0)
        self.n_step             = ckpt.get('n_step', 3)
        rs = ckpt.get('reward_stats')
        if rs is not None and self._reward_stats is not None:
            if 'mean' in rs:
                # Ancien format (un seul normalisateur partagé) : réutilisé pour les deux régimes
                for regime_stats in self._reward_stats.values():
                    regime_stats.mean  = rs['mean']
                    regime_stats.var   = rs['var']
                    regime_stats.count = rs['count']
            else:
                for regime, regime_rs in rs.items():
                    key = int(regime)
                    if key in self._reward_stats:
                        self._reward_stats[key].mean  = regime_rs['mean']
                        self._reward_stats[key].var   = regime_rs['var']
                        self._reward_stats[key].count = regime_rs['count']
