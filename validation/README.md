# TCDRM‑ADAPTIVE — Guide d’utilisation du JAR (Validation)

Cette documentation décrit l’usage du JAR ombré (shaded) pour générer les sorties (images/CSV) lors de la validation RL.

## Prérequis

- Java 17+
- Maven 3.9+ (pour construire le JAR si nécessaire)
- Python 3.11+ avec `uv` (client RL)

## Sorties produites

- Images: `images/*.png`
- CSV: `metrics/*.csv`
  - RL: `rl_qlearning_*`, `rl_dqn_*`, `summary_phase2_rl.csv`, `log_overtime.csv`

## Méthodes réellement utilisées pour la validation (org.tcdrm.adaptive.api.TcdrmAdapter)

- `initSimulation()`
  - Définition: Réinitialise la configuration d’exécution (équivalent `resetConfig()`), point de départ recommandé avant tout run.
  - Exemple:
    ```java
    TcdrmAdapter.initSimulation();
    ```

- `setExecRegion(String region)`
  - Définition: Définit la région d’exécution/agrégation (ex. "EU", "US", "AS", 'RANDOM').
  - Exemple:
    ```java
    TcdrmAdapter.setExecRegion("RANDOM");
    ```

- `setMaxQueries(int queries)`
  - Définition: Fixe le nombre de requêtes à exécuter pour le scénario.
  - Exemple:
    ```java
    TcdrmAdapter.setMaxQueries(1000);
    ```

- `runQlearningBoth(int timeoutSec)`
  - Définition: Exécute Q‑Learning en Simple puis Complex dans une seule session Python; génère PNG/CSV sous `images/` et `metrics/`.
  - Exemple:
    ```java
    TcdrmAdapter.runQlearningBoth(120); // attend jusqu’à 120s le client Python
    ```

- `runDqnBoth(int timeoutSec)`
  - Définition: Exécute DQN en Simple puis Complex dans une seule session Python; génère PNG/CSV sous `images/` et `metrics/`.
  - Exemple:
    ```java
    TcdrmAdapter.runDqnBoth(120);
    ```

- `runQlearningVsDqnBoth(int timeoutSec)`
  - Définition: Compare Q‑Learning vs DQN (Simple puis Complex) dans une seule session Python; génère les figures comparatives (réponse, coût cumulatif, prix moyen, consommation BW, réplicas).
  - Exemple:
    ```java
    TcdrmAdapter.runQlearningVsDqnBoth(120);
    ```

### Exemple minimal regroupé

```java
import org.tcdrm.adaptive.api.TcdrmAdapter;

public class MyValidation {
    public static void main(String[] args) {
        TcdrmAdapter.initSimulation();
        TcdrmAdapter.setExecRegion("RANDOM");
        TcdrmAdapter.setMaxQueries(1000);

        TcdrmAdapter.runQlearningBoth(120);
        TcdrmAdapter.runDqnBoth(120);
        TcdrmAdapter.runQlearningVsDqnBoth(120);
    }
}
```

## Compilation & Exécution (à la fin)

### Démarrer le client Python (RL)

```bash
cd validation/python
uv sync
uv run python connect_to_java.py --port 25333 \
  --qlearning-model ../models/qlearning_cloudsim.pkl \
  --dqn-model       ../models/dqn_cloudsim.pt
```

### Construire le JAR et lancer la validation (runners fournis)

```bash
cd validation
bash run.sh

# Les runners Java génèrent les PNG/CSV sous validation/{images,metrics}
#   - QLearningEvaluation (Q‑Learning: Simple → Complex)
#   - DNNEvaluation     (DQN:       Simple → Complex)
#   - RLComparisonEvaluation (comparaison 2‑modèles)
```

Notes

- Le port Py4J par défaut est `25333` (redirigeable via `TCDRM_PY4J_PORT`).
- Les modèles RL sont lus depuis `validation/models`.
- Assurez‑vous qu’un seul client Python est connecté pour chaque session Java (un Gateway par run).
- Libérez les ports 25333 (Gateway) et 25334 (callback) avant un nouveau run si besoin.
