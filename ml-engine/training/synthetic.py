"""
Синтетические поведенческие данные. НЕ реальные пользователи.

Нужны, чтобы проверить конвейер обучения и оценки до пилота с банком.
Метрики на синтетике показывают, что конвейер работает, но не качество на реальных мошенниках.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

from app.features import FEATURE_NAMES

# Диапазоны личных базовых уровней пользователей (мс, px/с, доля, символы/мин).
BASE_RANGES: dict[str, tuple[float, float]] = {
    "tap_duration_ms": (80, 180),
    "tap_interval_ms": (300, 900),
    "swipe_speed_px_s": (600, 1400),
    "scroll_speed_px_s": (400, 1200),
    "key_interval_ms": (150, 320),
    "chars_per_minute": (120, 260),
    "backspace_ratio": (0.02, 0.12),
    "screen_dwell_ms": (2000, 9000),
}

# Внутрисессионная изменчивость (логнормальный шум).
SESSION_CV = 0.2
# Доля пропущенных признаков в реальных данных (короткие сессии, отсутствующие жесты).
MISSING_RATE = 0.05


@dataclass(frozen=True)
class SyntheticUser:
    baseline: dict[str, float]


def sample_user(rng: np.random.Generator) -> SyntheticUser:
    base = {}
    for name in FEATURE_NAMES:
        lo, hi = BASE_RANGES[name]
        base[name] = float(rng.uniform(lo, hi))
    return SyntheticUser(baseline=base)


def _draw(rng: np.random.Generator, base: float, name: str) -> float:
    value = base * math.exp(rng.normal(0.0, SESSION_CV))
    if name == "backspace_ratio":
        value = min(max(value, 0.0), 1.0)
    return float(value)


def normal_session(rng: np.random.Generator, user: SyntheticUser) -> dict[str, float | None]:
    out: dict[str, float | None] = {}
    for name in FEATURE_NAMES:
        out[name] = None if rng.random() < MISSING_RATE else _draw(rng, user.baseline[name], name)
    return out


def impostor_session(rng: np.random.Generator, other: SyntheticUser) -> dict[str, float | None]:
    """Сессия другого человека: поведение совсем не похоже на профиль владельца."""
    return normal_session(rng, other)


def shifted_session(rng: np.random.Generator, user: SyntheticUser, kind: str) -> dict[str, float | None]:
    """Сессия того же пользователя с искажёнными признаками (сценарии из раздела 6 ТЗ)."""
    b = dict(user.baseline)
    if kind == "typing":
        # Быстрый набор (пример ТЗ: 180 -> 310 симв./мин)
        b["key_interval_ms"] /= 1.8
        b["chars_per_minute"] *= 1.8
    elif kind == "rushed":
        # Быстрые действия, короткое время на экранах (пример ТЗ: форма 18 -> 6 сек)
        b["tap_interval_ms"] /= 2.8
        b["screen_dwell_ms"] /= 2.5
        b["swipe_speed_px_s"] *= 2.2
    elif kind == "scripted":
        # Очень ровный ритм: низкая вариативность, но здесь моделируем через смещение
        b["tap_duration_ms"] = 35.0
        b["key_interval_ms"] = 60.0
        b["chars_per_minute"] = 900.0
    else:
        raise ValueError(kind)
    return normal_session(rng, SyntheticUser(baseline=b))


ANOMALY_KINDS = ("impostor", "typing", "rushed", "scripted")


def anomalous_session(
    rng: np.random.Generator,
    user: SyntheticUser,
    other: SyntheticUser,
    kind: str,
) -> dict[str, float | None]:
    if kind == "impostor":
        return impostor_session(rng, other)
    return shifted_session(rng, user, kind)
