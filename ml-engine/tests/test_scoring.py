import math

import numpy as np
import pytest

from app.features import FEATURE_NAMES
from app.scoring import (
    FeatureStats,
    InsufficientData,
    iso_score,
    profile_score,
    score_session,
    z_scores,
)


class FakeModel:
    """Модель с заранее заданным решением: положительное = норма."""

    def __init__(self, decision: float):
        self.decision = decision

    def decision_function(self, X):
        return np.array([self.decision] * len(X))


def stats_all(mean=100.0, std=10.0, n=8):
    return {name: FeatureStats(mean=mean, std=std, n=n) for name in FEATURE_NAMES}


def features_all(value=100.0):
    return {name: value for name in FEATURE_NAMES}


def test_z_scores_relative_to_personal_profile():
    z = z_scores(features_all(130.0), stats_all(mean=100.0, std=10.0))
    assert all(math.isclose(v, 3.0) for v in z.values())


def test_missing_feature_is_skipped_not_zero_scored():
    feats = features_all(100.0)
    feats["tap_duration_ms"] = None
    z = z_scores(feats, stats_all())
    assert "tap_duration_ms" not in z
    assert len(z) == len(FEATURE_NAMES) - 1


def test_profile_with_too_few_observations_is_ignored():
    st = stats_all()
    st["swipe_speed_px_s"] = FeatureStats(mean=100.0, std=10.0, n=2)
    z = z_scores(features_all(500.0), st)
    assert "swipe_speed_px_s" not in z


def test_std_floor_prevents_explosion_on_constant_profile():
    st = stats_all(mean=100.0, std=0.0)
    z = z_scores(features_all(101.0), st)
    assert all(abs(v) < 2.0 for v in z.values())


def test_profile_score_reacts_to_single_strong_deviation():
    z = {name: 0.5 for name in FEATURE_NAMES}
    base = profile_score(z)
    z["key_interval_ms"] = 4.0
    assert profile_score(z) > base + 0.2


def test_profile_score_bounds():
    assert profile_score({}) == 0.0
    assert profile_score({n: 100.0 for n in FEATURE_NAMES}) == 1.0


def test_iso_score_monotonic_and_bounded():
    assert iso_score(0.2) < iso_score(0.0) < iso_score(-0.2)
    assert 0.0 <= iso_score(-5) <= 1.0


def test_normal_session_gets_low_risk():
    res = score_session(features_all(100.0), stats_all(), FakeModel(decision=0.1))
    assert res.risk_score < 20
    assert res.reasons == []


def test_anomalous_session_gets_high_risk_and_reasons():
    feats = features_all(100.0)
    feats["key_interval_ms"] = 300.0  # z = 20
    res = score_session(feats, stats_all(), FakeModel(decision=-0.2))
    assert res.risk_score >= 70
    assert "TYPING_PATTERN_ANOMALY" in res.reasons


def test_multivariate_reason_when_no_single_feature_is_extreme():
    feats = {name: 100.0 + 2.5 * 10 for name in FEATURE_NAMES}  # z = 2.5 everywhere
    res = score_session(feats, stats_all(), FakeModel(decision=-0.3))
    assert res.reasons == ["MULTIVARIATE_ANOMALY"]


def test_insufficient_coverage_raises_unknown_path():
    feats = {name: None for name in FEATURE_NAMES}
    feats["tap_duration_ms"] = 100.0
    with pytest.raises(InsufficientData):
        score_session(feats, stats_all(), FakeModel(decision=0.0))


def test_risk_is_clamped_to_0_100():
    res = score_session(features_all(1e6), stats_all(), FakeModel(decision=-50))
    assert 0 <= res.risk_score <= 100


def test_extreme_single_feature_deviation_is_at_least_high_even_if_model_is_calm():
    feats = features_all(100.0)
    feats["key_interval_ms"] = 100.0 + 10 * 12  # z = 12 при std 10
    res = score_session(feats, stats_all(), FakeModel(decision=0.2))  # модель считает норму
    assert res.risk_score >= 70
    assert "TYPING_PATTERN_ANOMALY" in res.reasons
