"""
Оценка качества на НЕЗАВИСИМЫХ синтетических пользователях (другой seed, не из обучения).
Считает метрики из раздела 10 ТЗ: precision, recall, FPR, ROC-AUC, PR-AUC, задержку.
Запуск: python -m training.evaluate
"""

from __future__ import annotations

import json
import statistics
import time
from pathlib import Path

import joblib
import numpy as np
from sklearn.metrics import average_precision_score, roc_auc_score

from app.scoring import score_session
from training.stats import profile_from_sessions
from training.synthetic import ANOMALY_KINDS, anomalous_session, normal_session, sample_user

SEED = 2024
TEST_USERS = 300
PROFILE_SESSIONS = 8
NORMAL_PER_USER = 20
ANOMALY_PER_USER = 20
MODELS_DIR = Path(__file__).resolve().parent.parent / "models"
THRESHOLDS = {"MEDIUM": 40, "HIGH": 70}


def metrics_at(y: np.ndarray, s: np.ndarray, threshold: int) -> dict[str, float]:
    pred = s >= threshold
    tp = int(np.sum(pred & (y == 1)))
    fp = int(np.sum(pred & (y == 0)))
    fn = int(np.sum(~pred & (y == 1)))
    tn = int(np.sum(~pred & (y == 0)))
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    fpr = fp / (fp + tn) if fp + tn else 0.0
    return {"threshold": threshold, "precision": round(precision, 3), "recall": round(recall, 3), "fpr": round(fpr, 3)}


def main() -> None:
    bundle = joblib.load(MODELS_DIR / "isolation_forest_v0.1.joblib")
    model = bundle["model"]
    rng = np.random.default_rng(SEED)

    labels: list[int] = []
    risks: list[int] = []
    kinds: list[str] = []
    latencies: list[float] = []

    for _ in range(TEST_USERS):
        user = sample_user(rng)
        other = sample_user(rng)
        profile = profile_from_sessions([normal_session(rng, user) for _ in range(PROFILE_SESSIONS)])

        for _ in range(NORMAL_PER_USER):
            feats = normal_session(rng, user)
            t0 = time.perf_counter()
            res = score_session(feats, profile, model)
            latencies.append((time.perf_counter() - t0) * 1000)
            labels.append(0)
            risks.append(res.risk_score)
            kinds.append("normal")

        for i in range(ANOMALY_PER_USER):
            kind = ANOMALY_KINDS[i % len(ANOMALY_KINDS)]
            feats = anomalous_session(rng, user, other, kind)
            res = score_session(feats, profile, model)
            labels.append(1)
            risks.append(res.risk_score)
            kinds.append(kind)

    y = np.array(labels)
    s = np.array(risks)
    report: dict = {
        "model_version": bundle["model_version"],
        "data": "synthetic (не реальные пользователи)",
        "test_users": TEST_USERS,
        "sessions": len(labels),
        "roc_auc": round(float(roc_auc_score(y, s)), 3),
        "pr_auc": round(float(average_precision_score(y, s)), 3),
        "operating_points": [dict(name=k, **metrics_at(y, s, v)) for k, v in THRESHOLDS.items()],
        "latency_ms": {
            "median": round(statistics.median(latencies), 3),
            "p95": round(float(np.percentile(latencies, 95)), 3),
        },
        "recall_by_anomaly_kind": {},
    }
    for kind in ANOMALY_KINDS:
        mask = np.array([k == kind for k in kinds])
        report["recall_by_anomaly_kind"][kind] = round(float(np.mean(s[mask] >= THRESHOLDS["HIGH"])), 3)

    out = MODELS_DIR / "evaluation_v0.1.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
