package kz.behaviorguard.backend.risk;

import kz.behaviorguard.backend.profile.BehavioralProfile;

import java.util.Map;

/** Граница с ML-сервисом. Отдельный интерфейс, чтобы в тестах подменять модель. */
public interface RiskModelClient {

    /**
     * @throws ModelInsufficientDataException если признаков мало для сравнения
     * @throws ModelUnavailableException если сервис недоступен или вернул ошибку
     */
    MlScore score(Map<String, Double> features, BehavioralProfile profile);
}
