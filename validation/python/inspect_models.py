import os, pickle
print('Inspecting Q-Learning pickle:')
path_q='models/qlearning_cloudsim.pkl'
if os.path.exists(path_q):
    with open(path_q,'rb') as f:
        obj=pickle.load(f)
    print('type:',type(obj))
    if isinstance(obj, dict):
        print('dict keys:', list(obj.keys())[:20])
        for k in list(obj.keys())[:3]:
            try:
                v=obj[k]
                print(' sample key type:', type(k))
                print('  value type:', type(v))
                if isinstance(v, dict):
                    print('   nested dict len:', len(v))
                elif hasattr(v,'__len__'):
                    print('   len:', len(v))
            except Exception as e:
                print(' sample inspect error:', e)
else:
    print('file missing')

print('\nInspecting DQN torch file:')
path_d='models/dqn_cloudsim.pt'
print('exists:', os.path.exists(path_d))
try:
    import torch
    TORCH=True
except Exception as e:
    TORCH=False
    print('torch import failed:', e)

if os.path.exists(path_d) and TORCH:
    try:
        m=torch.jit.load(path_d, map_location='cpu')
        print('loaded via torch.jit:', type(m))
    except Exception as e:
        print('jit load failed:', e)
        try:
            m=torch.load(path_d, map_location='cpu')
            print('torch.load type:', type(m))
            if isinstance(m, dict):
                print('dict keys:', list(m.keys())[:10])
        except Exception as e2:
            print('torch.load failed:', e2)
