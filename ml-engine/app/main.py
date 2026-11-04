"""
ML Engine: HTTP-обёртка над скорингом. Сервис не хранит профили: профиль приходит от backend
(там он лежит в PostgreSQL). Поэтому сервис можно масштабировать без состояния.
"""

from __future__ import annotations

import hmac
import math
import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

import joblib
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, field_validator

from app.features import FEATURE_NAMES, MODEL_VERSION
from app.scoring import FeatureStats, InsufficientData, score_session

DEFAULT_MODEL = Path(__file__).resolve().parent.parent / "models" / f"isolation_forest_{MODEL_VERSION}.joblib"


class FeatureStatsIn(BaseModel):
    mean: float
    std: float = Field(ge=0)
    n: int = Field(ge=0)


class ProfileIn(BaseModel):
    session_count: int = Field(ge=0)
    stats: dict[str, FeatureStatsIn] = Field(default_factory=dict)


class ScoreRequest(BaseModel):
    features: dict[str, float | None]
    profile: ProfileIn

    @field_validator("features")
    @classmethod
    def only_known_features(cls, v: dict[str, float | None]) -> dict[str, float | None]:
        unknown = set(v) - set(FEATURE_NAMES)
        if unknown:
            raise ValueError(f"неизвестные признаки: {sorted(unknown)}")
        for name, value in v.items():
            if value is not None and not math.isfinite(value):
                raise ValueError(f"признак {name} не конечное число")
        return v


class ScoreResponse(BaseModel):
    risk_score: int
    anomaly_score: float
    profile_score: float
    reasons: list[str]
    z_scores: dict[str, float]
    model_version: str


def create_app(model_path: Path | None = None, service_token: str | None = None) -> FastAPI:
    path = model_path or Path(os.environ.get("BG_MODEL_PATH", DEFAULT_MODEL))
    # Служебный токен: ML-сервис отвечает только backend с этим токеном. Пустой токен — оценок нет.
    token = service_token if service_token is not None else os.environ.get("ML_SERVICE_TOKEN", "")
    state: dict[str, Any] = {}

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        if path.exists():
            bundle = joblib.load(path)
            state["model"] = bundle["model"]
            state["version"] = bundle["model_version"]
        yield

    app = FastAPI(title="BehaviorGuard ML Engine", version=MODEL_VERSION, lifespan=lifespan)

    @app.exception_handler(RequestValidationError)
    async def validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
        # Не возвращаем входные значения: в них может оказаться inf/NaN (не сериализуется в JSON)
        # и любые данные клиента, которые не нужно эхом отдавать назад.
        errors = [{"loc": list(e["loc"]), "msg": e["msg"], "type": e["type"]} for e in exc.errors()]
        return JSONResponse(status_code=422, content={"detail": errors})

    @app.get("/health")
    def health() -> dict[str, Any]:
        return {"status": "UP", "model_loaded": "model" in state, "model_version": state.get("version")}

    @app.post("/v1/score", response_model=ScoreResponse)
    def score(req: ScoreRequest, x_bg_service_token: str | None = Header(default=None)) -> ScoreResponse:
        if not token:
            raise HTTPException(status_code=503, detail="служебный токен не настроен")
        if x_bg_service_token is None or not hmac.compare_digest(x_bg_service_token.encode(), token.encode()):
            raise HTTPException(status_code=401, detail="неверный служебный токен")
        if "model" not in state:
            raise HTTPException(status_code=503, detail="модель не загружена")
        stats = {k: FeatureStats(mean=v.mean, std=v.std, n=v.n) for k, v in req.profile.stats.items()}
        try:
            result = score_session(req.features, stats, state["model"])
        except InsufficientData as e:
            raise HTTPException(status_code=422, detail=str(e)) from e
        return ScoreResponse(
            risk_score=result.risk_score,
            anomaly_score=result.anomaly_score,
            profile_score=result.profile_score,
            reasons=result.reasons,
            z_scores=result.z_scores,
            model_version=state["version"],
        )

    return app


app = create_app()
