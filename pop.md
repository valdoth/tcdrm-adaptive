1. archi

"Pour éviter le coût prohibitif de l'exploration initiale (Cold Start Problem) inhérent à l'apprentissage par renforcement, notre framework TCDRM-ADAPTIVE adopte une approche en deux phases. Premièrement, un pré-entraînement hors ligne sur simulateur permet aux modèles (Q-Learning et DQN) d'assimiler les dynamiques de coût et de latence du multi-cloud. Deuxièmement, les modèles sont déployés avec un mécanisme d'apprentissage en ligne (Online Learning) actif. Cela leur permet d'affiner continuellement leur politique de décision en s'adaptant dynamiquement aux fluctuations de popularité des données et aux variations de charge, garantissant ainsi le respect des SLAs et du budget du locataire tout au long du cycle de vie de l'application."


