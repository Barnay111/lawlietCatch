import joblib
import pytest
from fastapi.testclient import TestClient
from sklearn.ensemble import IsolationForest
import numpy as np

from app.features import FEATURE_NAMES
from app.main import create_app


@pytest.fixture(scope="module")
def client(tmp_path_factory):
    rng = np.random.default_rng(0)
    model = IsolationForest(n_estimators=50, random_state=0).fit(rng.normal(size=(300, len(FEATURE_NAMES))))
    path = tmp_path_factory.mktemp("m") / "m.joblib"
    joblib.dump({"model": model, "model_version": "test-v"}, path)
    with TestClient(create_app(path, service_token=TOKEN)) as c:
        c.headers.update({"X-BG-Service-Token": TOKEN})
        yield c


TOKEN = "test-service-token-0123456789"


def test_score_without_service_token_is_rejected(tmp_path):
    rng = np.random.default_rng(0)
    model = IsolationForest(n_estimators=10, random_state=0).fit(rng.normal(size=(100, len(FEATURE_NAMES))))
    path = tmp_path / "m.joblib"
    joblib.dump({"model": model, "model_version": "test-v"}, path)
    with TestClient(create_app(path, service_token=TOKEN)) as c:
        r = c.post("/v1/score", json={"features": {f: 100 for f in FEATURE_NAMES}, "profile": profile()})
        assert r.status_code == 401
        r = c.post("/v1/score", json={"features": {f: 100 for f in FEATURE_NAMES}, "profile": profile()},
                   headers={"X-BG-Service-Token": "wrong"})
        assert r.status_code == 401


def test_no_token_configured_means_no_scores(tmp_path):
    rng = np.random.default_rng(0)
    model = IsolationForest(n_estimators=10, random_state=0).fit(rng.normal(size=(100, len(FEATURE_NAMES))))
    path = tmp_path / "m.joblib"
    joblib.dump({"model": model, "model_version": "test-v"}, path)
    with TestClient(create_app(path, service_token="")) as c:
        r = c.post("/v1/score", json={"features": {f: 100 for f in FEATURE_NAMES}, "profile": profile()},
                   headers={"X-BG-Service-Token": "anything"})
        assert r.status_code == 503


def profile(n=8):
    return {"session_count": n, "stats": {f: {"mean": 100, "std": 10, "n": n} for f in FEATURE_NAMES}}


def test_health_is_public_without_token(tmp_path):
    rng = np.random.default_rng(0)
    model = IsolationForest(n_estimators=10, random_state=0).fit(rng.normal(size=(100, len(FEATURE_NAMES))))
    path = tmp_path / "m.joblib"
    joblib.dump({"model": model, "model_version": "test-v"}, path)
    with TestClient(create_app(path, service_token=TOKEN)) as c:
        assert c.get("/health").status_code == 200


def test_health_reports_model(client):
    body = client.get("/health").json()
    assert body["model_loaded"] is True
    assert body["model_version"] == "test-v"


def test_score_returns_contract_fields(client):
    r = client.post("/v1/score", json={"features": {f: 100 for f in FEATURE_NAMES}, "profile": profile()})
    assert r.status_code == 200
    body = r.json()
    assert set(body) == {"risk_score", "anomaly_score", "profile_score", "reasons", "z_scores", "model_version"}
    assert 0 <= body["risk_score"] <= 100


def test_unknown_feature_is_rejected(client):
    feats = {f: 100 for f in FEATURE_NAMES}
    feats["keystroke_text"] = 1.0
    r = client.post("/v1/score", json={"features": feats, "profile": profile()})
    assert r.status_code == 422


def test_insufficient_data_returns_422_for_backend_to_map_to_unknown(client):
    feats = {f: None for f in FEATURE_NAMES}
    feats["tap_duration_ms"] = 100
    r = client.post("/v1/score", json={"features": feats, "profile": profile()})
    assert r.status_code == 422


def test_non_finite_value_is_rejected(client):
    # Нестандартный JSON-литерал Infinity: строгий парсер его не принимает, значит 422.
    body = ('{"features": {"tap_duration_ms": Infinity, "tap_interval_ms": 1, "swipe_speed_px_s": 1, '
            '"scroll_speed_px_s": 1, "key_interval_ms": 1, "chars_per_minute": 1, "backspace_ratio": 1, '
            '"screen_dwell_ms": 1}, "profile": {"session_count": 8, "stats": {}}}')
    r = client.post("/v1/score", content=body, headers={"Content-Type": "application/json"})
    assert r.status_code == 422
