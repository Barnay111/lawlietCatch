"""
Выбор формулы профиля на ВАЛИДАЦИОННОМ seed (не тестовом).
Сравниваем: среднее |z| (текущая) против смеси максимума и среднего.
"""
import sys
import numpy as np
import app.scoring as sc
from sklearn.metrics import roc_auc_score
import joblib
from pathlib import Path
from training.stats import profile_from_sessions
from training.synthetic import ANOMALY_KINDS, anomalous_session, normal_session, sample_user

VAL_SEED = 7
MODEL = joblib.load(Path(__file__).resolve().parent.parent / "models/isolation_forest_v0.1.joblib")["model"]

def mean_profile(z):
    if not z: return 0.0
    return min(1.0, sum(min(abs(v), 6.0) for v in z.values()) / len(z) / 3.0)

def mixed_profile(z):
    if not z: return 0.0
    mx = max(abs(v) for v in z.values())
    return 0.5 * min(1.0, mx / 4.0) + 0.5 * mean_profile(z)

def run(fn, label):
    rng = np.random.default_rng(VAL_SEED)
    sc.profile_score = fn
    y, s, kinds = [], [], []
    for _ in range(200):
        u, o = sample_user(rng), sample_user(rng)
        prof = profile_from_sessions([normal_session(rng, u) for _ in range(8)])
        for _ in range(20):
            y.append(0); s.append(sc.score_session(normal_session(rng, u), prof, MODEL).risk_score); kinds.append("normal")
        for i in range(20):
            k = ANOMALY_KINDS[i % 4]
            y.append(1); s.append(sc.score_session(anomalous_session(rng, u, o, k), prof, MODEL).risk_score); kinds.append(k)
    y, s, kinds = np.array(y), np.array(s), np.array(kinds)
    rec = {k: round(float(np.mean(s[kinds == k] >= 70)), 3) for k in ANOMALY_KINDS}
    fpr = float(np.mean(s[y == 0] >= 70))
    print(label, "AUC", round(roc_auc_score(y, s), 3), "recall@70 by kind", rec, "FPR@70", round(fpr, 3))

run(mean_profile, "mean_only")
run(mixed_profile, "max+mean")
