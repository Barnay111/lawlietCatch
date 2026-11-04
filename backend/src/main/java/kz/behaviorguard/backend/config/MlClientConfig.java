package kz.behaviorguard.backend.config;

import kz.behaviorguard.backend.risk.HttpRiskModelClient;
import kz.behaviorguard.backend.risk.RiskModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class MlClientConfig {

    @Bean
    RiskModelClient riskModelClient(@Value("${behaviorguard.ml.base-url}") String baseUrl,
                                    @Value("${behaviorguard.ml.timeout-ms:1500}") int timeoutMs,
                                    @Value("${behaviorguard.ml.token:}") String serviceToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (!serviceToken.isBlank()) {
            // Служебный токен: ML-сервис принимает запросы только с ним (сервис-к-сервису).
            builder.defaultHeader("X-BG-Service-Token", serviceToken);
        }
        RestClient rest = builder.build();
        return new HttpRiskModelClient(rest);
    }
}
