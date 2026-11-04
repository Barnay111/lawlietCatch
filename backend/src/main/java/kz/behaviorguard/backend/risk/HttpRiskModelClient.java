package kz.behaviorguard.backend.risk;

import kz.behaviorguard.backend.profile.BehavioralProfile;
import kz.behaviorguard.backend.profile.Welford;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpRiskModelClient implements RiskModelClient {

    private final RestClient http;

    public HttpRiskModelClient(RestClient http) {
        this.http = http;
    }

    @Override
    public MlScore score(Map<String, Double> features, BehavioralProfile profile) {
        Map<String, Object> body = new HashMap<>();
        body.put("features", features);
        Map<String, Object> stats = new LinkedHashMap<>();
        for (String name : features.keySet()) {
            Map<String, Object> raw = profile.statsFor(name);
            if (raw != null) {
                Map<String, Object> s = Welford.summary(raw);
                stats.put(name, Map.of("mean", s.get("mean"), "std", s.get("std"), "n", s.get("n")));
            }
        }
        body.put("profile", Map.of("session_count", profile.getSessionCount(), "stats", stats));

        Map<String, Object> resp;
        try {
            resp = http.post()
                    .uri("/v1/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new ModelInsufficientDataException("ML: недостаточно признаков");
        } catch (RestClientException e) {
            throw new ModelUnavailableException("ML-сервис недоступен", e);
        }
        if (resp == null) {
            throw new ModelUnavailableException("ML-сервис вернул пустой ответ", null);
        }
        List<String> reasons = new ArrayList<>();
        for (Object r : (List<?>) resp.get("reasons")) {
            reasons.add(String.valueOf(r));
        }
        return new MlScore(
                ((Number) resp.get("risk_score")).intValue(),
                ((Number) resp.get("anomaly_score")).doubleValue(),
                ((Number) resp.get("profile_score")).doubleValue(),
                reasons,
                String.valueOf(resp.get("model_version")));
    }
}
