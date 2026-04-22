import os
import json
from glob import glob


def find_best(logs_root: str) -> dict:
    candidates = []
    for agent in ['qlearning', 'dqn']:
        pattern = os.path.join(logs_root, agent, '*', 'best_meta.json')
        for meta_path in glob(pattern):
            try:
                with open(meta_path, 'r') as f:
                    meta = json.load(f)
                meta['_agent'] = agent
                meta['_meta_path'] = meta_path
                meta['_log_dir'] = os.path.dirname(meta_path)
                candidates.append(meta)
            except Exception:
                pass
    if not candidates:
        return {}
    # Primary: highest best_avg10_reward
    candidates.sort(key=lambda m: (m.get('best_avg10_reward', float('-inf'))), reverse=True)
    top = candidates[0]
    # Try to enrich with cost/sla if available
    mt = top.get('metrics_tail', {}) or {}
    score = top.get('best_avg10_reward', 0)
    # Optionally penalize SLA violations and cost
    try:
        sla = float(mt.get('sla_violations', 0))
        cost = float(mt.get('cumulative_cost', 0.0))
        score_adj = score - 0.01 * sla - 0.0001 * cost
        top['_score_adjusted'] = score_adj
    except Exception:
        top['_score_adjusted'] = score
    return top


def main():
    here = os.path.dirname(__file__)
    logs_root = os.path.abspath(os.path.join(here, '..', 'logs'))
    best = find_best(logs_root)
    if not best:
        print('No best_meta.json found under', logs_root)
        return 1
    out_path = os.path.join(logs_root, 'BEST_SELECTION.json')
    with open(out_path, 'w') as f:
        json.dump(best, f, indent=2)
    print('Best model selection written to', out_path)
    print('Agent:', best.get('_agent'))
    print('From:', best.get('_log_dir'))
    print('Score (avg10):', best.get('best_avg10_reward'))
    print('Adjusted:', best.get('_score_adjusted'))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
