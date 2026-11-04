"""
Скоринг сессии: отклонение от профиля пользователя + Isolation Forest.
Чистые функции, без FastAPI: их легко тестировать.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from app.features import (
    FEATURE_NAMES,
    FEATURE_REASON,
    MIN_COVERAGE,
    MIN_PROFILE_N,
    SD_FLOOR_FRACTION,
    EXTREME_Z,
    Z_REASON_THRESHOLD,
)

W_ISO = 0.6
W_PROFILE = 0.4
# Крутизна сигмоиды, переводящей решение IsolationForest в [0, 1].
ISO_SLOPE = 10.0


@dataclass(frozen=True)
class FeatureStats:
    mean: float
    std: float
    n: int


class InsufficientData(Exception):
    """Недостаточно признаков для сравнения с профилем."""


@dataclass(frozen=True)
class ScoreResult:
    risk_score: int
    anomaly_score: float
    profile_score: float
    reasons: list[str]
    z_scores: dict[str, float]


def z_scores(
    features: dict[str, float | None],
    stats: dict[str, FeatureStats],
) -> dict[str, float]:
    """Отклонения от личного профиля. Признаки без данных в профиле пропускаются."""
    out: dict[str, float] = {}
    for name in FEATURE_NAMES:
        x = features.get(name)
        st = stats.get(name)
        if x is None or st is None or st.n < MIN_PROFILE_N:
            continue
        if not math.isfinite(x):
            continue
        sd = max(st.std, SD_FLOOR_FRACTION * abs(st.mean), 1e-9)
        out[name] = (x - st.mean) / sd
    return out


def to_vector(z: dict[str, float]) -> np.ndarray:
    """Вектор для модели. Признак без данных — 0 (типичное значение)."""
    return np.array([z.get(name, 0.0) for name in FEATURE_NAMES], dtype=float)


def profile_score(z: dict[str, float]) -> float:
    """
    Смесь максимума и среднего |z|.
    Одно сильно отклонившееся поле (например, темп набора) не должно растворяться
    среди нормальных. Выбрано на валидационном seed (training/tune_profile.py).
    """
    if not z:
        return 0.0
    abs_values = [min(abs(v), 6.0) for v in z.values()]
    mean_part = min(1.0, (sum(abs_values) / len(abs_values)) / 3.0)
    max_part = min(1.0, max(abs_values) / 4.0)
    return 0.5 * max_part + 0.5 * mean_part


def iso_score(decision: float) -> float:
    """decision_function < 0 — аномалия. Переводим в [0, 1] сигмоидой."""
    return 1.0 / (1.0 + math.exp(ISO_SLOPE * decision))


def score_session(
    features: dict[str, float | None],
    stats: dict[str, FeatureStats],
    model,
) -> ScoreResult:
    z = z_scores(features, stats)
    coverage = len(z) / len(FEATURE_NAMES)
    if coverage < MIN_COVERAGE:
        raise InsufficientData(f"доступно признаков: {len(z)} из {len(FEATURE_NAMES)}")

    decision = float(model.decision_function(to_vector(z).reshape(1, -1))[0])
    iso = iso_score(decision)
    prof = profile_score(z)

    risk = round(100 * (W_ISO * iso + W_PROFILE * prof))
    # Isolation Forest слабо реагирует на экстремум по одному признаку при нормальных остальных.
    # Статистическое правило: отклонение от профиля на EXTREME_Z и больше — минимум HIGH.
    max_abs_z = max((abs(v) for v in z.values()), default=0.0)
    if max_abs_z >= EXTREME_Z:
        risk = max(risk, 70)
    risk = max(0, min(100, risk))

    reasons: list[str] = []
    for name, value in sorted(z.items(), key=lambda kv: -abs(kv[1])):
        if abs(value) >= Z_REASON_THRESHOLD:
            code = FEATURE_REASON[name]
            if code not in reasons:
                reasons.append(code)
    if not reasons and iso >= 0.7:
        reasons.append("MULTIVARIATE_ANOMALY")

    return ScoreResult(
        risk_score=risk,
        anomaly_score=round(iso, 4),
        profile_score=round(prof, 4),
        reasons=reasons,
        z_scores={k: round(v, 3) for k, v in z.items()},
    )
