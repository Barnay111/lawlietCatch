from pathlib import Path

import pytest

from app.main import DEFAULT_MODEL


@pytest.mark.skipif(not DEFAULT_MODEL.exists(), reason="модель не обучена")
def test_trained_model_passes_smoke_check():
    import joblib
    from app.features import FEATURE_NAMES
    from app.scoring import FeatureStats, score_session

    bundle = joblib.load(DEFAULT_MODEL)
    stats = {f: FeatureStats(mean=100, std=10, n=8) for f in FEATURE_NAMES}
    normal = score_session({f: 101.0 for f in FEATURE_NAMES}, stats, bundle["model"])
    assert normal.risk_score < 40
    assert bundle["model_version"] == "v0.1"
