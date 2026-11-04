"""
Обучение Isolation Forest на z-векторах нормальных сессий.
Запуск: python -m training.train
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest

from app.features import MODEL_VERSION
from app.scoring import z_scores, to_vector
from training.stats import profile_from_sessions
from training.synthetic import normal_session, sample_user

SEED = 42
TRAIN_USERS = 400
PROFILE_SESSIONS = 8
TRAIN_SESSIONS_PER_USER = 15

MODELS_DIR = Path(__file__).resolve().parent.parent / "models"


def build_training_matrix(rng: np.random.Generator) -> np.ndarray:
    rows = []
    for _ in range(TRAIN_USERS):
        user = sample_user(rng)
        profile = profile_from_sessions([normal_session(rng, user) for _ in range(PROFILE_SESSIONS)])
        for _ in range(TRAIN_SESSIONS_PER_USER):
            z = z_scores(normal_session(rng, user), profile)
            if z:
                rows.append(to_vector(z))
    return np.vstack(rows)


def main() -> None:
    rng = np.random.default_rng(SEED)
    X = build_training_matrix(rng)
    model = IsolationForest(n_estimators=300, max_samples=256, random_state=SEED)
    model.fit(X)

    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    path = MODELS_DIR / f"isolation_forest_{MODEL_VERSION}.joblib"
    joblib.dump(
        {
            "model": model,
            "model_version": MODEL_VERSION,
            "trained_at": datetime.now(timezone.utc).isoformat(),
            "n_train_rows": int(X.shape[0]),
            "seed": SEED,
            "data": "synthetic",
        },
        path,
    )
    meta = {"path": str(path), "rows": int(X.shape[0]), "model_version": MODEL_VERSION, "data": "synthetic"}
    json.dump(meta, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
