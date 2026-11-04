"""
Контракт признаков между backend и ML-сервисом.
Порядок и имена должны совпадать с FeatureExtractor в backend (Java).
Меняется только вместе с версией модели.
"""

FEATURE_NAMES: tuple[str, ...] = (
    "tap_duration_ms",
    "tap_interval_ms",
    "swipe_speed_px_s",
    "scroll_speed_px_s",
    "key_interval_ms",
    "chars_per_minute",
    "backspace_ratio",
    "screen_dwell_ms",
)

# Признак -> код причины для ответа API (раздел 5 ТЗ: reasons).
FEATURE_REASON: dict[str, str] = {
    "tap_duration_ms": "TAP_TIMING_ANOMALY",
    "tap_interval_ms": "TAP_TIMING_ANOMALY",
    "swipe_speed_px_s": "SWIPE_SPEED_ANOMALY",
    "scroll_speed_px_s": "SCROLL_SPEED_ANOMALY",
    "key_interval_ms": "TYPING_PATTERN_ANOMALY",
    "chars_per_minute": "TYPING_PATTERN_ANOMALY",
    "backspace_ratio": "CORRECTION_PATTERN_ANOMALY",
    "screen_dwell_ms": "NAVIGATION_ANOMALY",
}

MODEL_VERSION = "v0.1"

# Минимум наблюдений по признаку в профиле, чтобы сравнивать с ним.
MIN_PROFILE_N = 3
# Нижняя граница стандартного отклонения: доля от среднего. Защита от почти нулевой дисперсии.
SD_FLOOR_FRACTION = 0.05
# Минимальная доля признаков, по которым можно сравнить сессию. Ниже — UNKNOWN.
MIN_COVERAGE = 0.5
# Порог |z|, с которого признак считается аномальным (для reasons).
Z_REASON_THRESHOLD = 3.0

# Экстремальное отклонение: |z| от личного профиля, начиная с которого сессия не может быть ниже HIGH.
# При 6 стандартных отклонениях вероятность такого значения у нормального поведения порядка 1e-9.
# Значение выбрано по этому аргументу, а не подбором под метрики.
EXTREME_Z = 6.0
