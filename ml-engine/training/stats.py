"""Статистики профиля. Той же формулы придерживается backend (Welford) при обновлении профиля."""

from __future__ import annotations

import numpy as np

from app.features import FEATURE_NAMES
from app.scoring import FeatureStats


def profile_from_sessions(sessions: list[dict[str, float | None]]) -> dict[str, FeatureStats]:
    stats: dict[str, FeatureStats] = {}
    for name in FEATURE_NAMES:
        values = [s[name] for s in sessions if s.get(name) is not None]
        if len(values) >= 2:
            stats[name] = FeatureStats(
                mean=float(np.mean(values)),
                std=float(np.std(values, ddof=1)),
                n=len(values),
            )
        elif len(values) == 1:
            stats[name] = FeatureStats(mean=float(values[0]), std=0.0, n=1)
    return stats
