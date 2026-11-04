package kz.behaviorguard.backend.risk;

import java.util.List;

/** Ответ ML-сервиса. Балл и причины считает ml-engine, пороги и рекомендации — backend. */
public record MlScore(int riskScore, double anomalyScore, double profileScore,
                      List<String> reasons, String modelVersion) {
}
