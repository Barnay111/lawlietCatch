package kz.behaviorguard.backend;

import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.profile.BehavioralProfile;
import kz.behaviorguard.backend.risk.MlScore;
import kz.behaviorguard.backend.risk.ModelUnavailableException;
import kz.behaviorguard.backend.risk.RiskModelClient;
import kz.behaviorguard.backend.security.ApiKey;
import kz.behaviorguard.backend.security.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, RiskApiTests.FakeModelConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class RiskApiTests {

    /** Подмена ML-сервиса: балл и ошибку задаёт сам тест. */
    static class FakeModel implements RiskModelClient {
        volatile int score = 10;
        volatile RuntimeException error;
        volatile List<String> reasons = List.of();

        @Override
        public MlScore score(Map<String, Double> features, BehavioralProfile profile) {
            if (error != null) throw error;
            return new MlScore(score, 0.5, 0.5, reasons, "v0.1");
        }
    }

    @TestConfiguration
    static class FakeModelConfig {
        @Bean
        @Primary
        FakeModel fakeModel() {
            return new FakeModel();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;
    @Autowired FakeModel model;
    @Autowired kz.behaviorguard.backend.user.AnalyzedUserRepository users;

    private record Tenant(String sdkKey, String serverKey) {
    }

    private Tenant newTenant() {
        Organization org = organizations.save(new Organization("Risk Bank " + System.nanoTime()));
        return new Tenant(
                apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey(),
                apiKeys.issue(org, ApiKey.Type.SERVER).rawKey());
    }

    private String createSession(String sdkKey, String userId) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", sdkKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    /** Типичная сессия: все основные признаки присутствуют. */
    private void addTypicalEvents(String sdkKey, String sid, double keyInterval) throws Exception {
        String now = Instant.now().toString();
        String body = "{\"sessionId\":\"" + sid + "\",\"events\":["
                + "{\"eventType\":\"TAP\",\"eventTime\":\"" + now + "\",\"payload\":{\"durationMs\":120,\"intervalMs\":500}},"
                + "{\"eventType\":\"SWIPE\",\"eventTime\":\"" + now + "\",\"payload\":{\"distancePx\":300,\"durationMs\":200,\"avgSpeedPxS\":1000,\"direction\":\"LEFT\"}},"
                + "{\"eventType\":\"SCROLL\",\"eventTime\":\"" + now + "\",\"payload\":{\"distancePx\":500,\"durationMs\":900,\"avgSpeedPxS\":600,\"direction\":\"DOWN\"}},"
                + "{\"eventType\":\"TEXT_INPUT\",\"eventTime\":\"" + now + "\",\"payload\":{\"fieldId\":\"transfer.amount\",\"keyCount\":20,"
                + "\"keyIntervalAvgMs\":" + keyInterval + ",\"keyIntervalStdMs\":30,\"charsPerMinute\":200,\"backspaceCount\":2}},"
                + "{\"eventType\":\"SCREEN_VIEW\",\"eventTime\":\"" + now + "\",\"payload\":{\"screen\":\"transfer\",\"dwellMs\":4000}}"
                + "]}";
        mvc.perform(post("/api/v1/events/batch").header("X-BG-Api-Key", sdkKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    private void finish(String sdkKey, String sid) throws Exception {
        mvc.perform(post("/api/v1/sessions/" + sid + "/finish").header("X-BG-Api-Key", sdkKey))
                .andExpect(status().isOk());
    }

    /** Создаёт завершённую сессию, учитываемую в профиле. */
    private void completedSession(Tenant t, String userId, double keyInterval) throws Exception {
        String sid = createSession(t.sdkKey(), userId);
        addTypicalEvents(t.sdkKey(), sid, keyInterval);
        finish(t.sdkKey(), sid);
    }

    private ResultActions assess(String serverKey, String sid) throws Exception {
        return mvc.perform(post("/api/v1/risk/assess").header("X-BG-Api-Key", serverKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + sid + "\",\"context\":{\"action\":\"TRANSFER\",\"newRecipient\":true}}"));
    }

    @Test
    void unknownWhileProfileIsStillLearning() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "new-client");
        addTypicalEvents(t.sdkKey(), sid, 230);
        assess(t.serverKey(), sid)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("UNKNOWN"))
                .andExpect(jsonPath("$.riskScore").doesNotExist())
                .andExpect(jsonPath("$.reasons[0]").value("INSUFFICIENT_PROFILE"))
                .andExpect(jsonPath("$.recommendation").value("FALLBACK_TO_RULES"))
                .andExpect(jsonPath("$.profileStatus").value("NEW"));
    }

    @Test
    void profileBecomesReadyAfterFiveCompletedSessions() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) {
            completedSession(t, "ready-client", 230 + i * 3);
        }
        String sid = createSession(t.sdkKey(), "ready-client");
        assess(t.serverKey(), sid)
                .andExpect(jsonPath("$.profileStatus").value("READY"));
    }

    @Test
    void profileEndpointReturnsStatisticsNotRawValues() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) {
            completedSession(t, "stat-client", 230);
        }
        UUID userId = users.findByOrganizationIdAndExternalUserId(
                orgIdOf(t.serverKey()), "stat-client").orElseThrow().getId();
        mvc.perform(get("/api/v1/users/" + userId + "/profile").header("X-BG-Api-Key", t.serverKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("READY"))
                .andExpect(jsonPath("$.sessionCount").value(5))
                .andExpect(jsonPath("$.features.key_interval_ms.n").value(5))
                .andExpect(jsonPath("$.features.key_interval_ms.std").exists())
                // сырые накопители Уэлфорда наружу не отдаём
                .andExpect(jsonPath("$.features.key_interval_ms.m2").doesNotExist());
    }

    private UUID orgIdOf(String serverKey) {
        return apiKeys.authenticate(serverKey).orElseThrow().organizationId();
    }

    @Test
    void normalSessionIsLowRisk() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) completedSession(t, "low-client", 230);
        String sid = createSession(t.sdkKey(), "low-client");
        addTypicalEvents(t.sdkKey(), sid, 230);
        model.error = null;
        model.score = 12;
        model.reasons = List.of();
        assess(t.serverKey(), sid)
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.riskScore").value(12))
                .andExpect(jsonPath("$.recommendation").value("NONE"));
    }

    @Test
    void anomalousSessionGetsHighRiskWithReasonAndStepUp() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) completedSession(t, "high-client", 230);
        String sid = createSession(t.sdkKey(), "high-client");
        addTypicalEvents(t.sdkKey(), sid, 90);
        model.error = null;
        model.score = 85;
        model.reasons = List.of("TYPING_PATTERN_ANOMALY");
        assess(t.serverKey(), sid)
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.recommendation").value("STEP_UP_AUTHENTICATION"))
                .andExpect(jsonPath("$.reasons[0]").value("TYPING_PATTERN_ANOMALY"));
    }

    @Test
    void mediumRiskRecommendsMonitoring() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) completedSession(t, "mid-client", 230);
        String sid = createSession(t.sdkKey(), "mid-client");
        addTypicalEvents(t.sdkKey(), sid, 230);
        model.error = null;
        model.score = 55;
        model.reasons = List.of();
        assess(t.serverKey(), sid)
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.recommendation").value("MONITOR"));
    }

    @Test
    void modelOutageNeverProducesLowRisk() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) completedSession(t, "outage-client", 230);
        String sid = createSession(t.sdkKey(), "outage-client");
        addTypicalEvents(t.sdkKey(), sid, 230);
        model.error = new ModelUnavailableException("тест: сервис недоступен", null);
        try {
            assess(t.serverKey(), sid)
                    .andExpect(jsonPath("$.riskLevel").value("UNKNOWN"))
                    .andExpect(jsonPath("$.riskScore").doesNotExist())
                    .andExpect(jsonPath("$.reasons[0]").value("MODEL_UNAVAILABLE"));
        } finally {
            model.error = null;
        }
    }

    @Test
    void finishingTwiceDoesNotCountSessionTwice() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "once-client");
        addTypicalEvents(t.sdkKey(), sid, 230);
        finish(t.sdkKey(), sid);
        finish(t.sdkKey(), sid);
        String sid2 = createSession(t.sdkKey(), "once-client");
        // Профиль из одной сессии: статус LEARNING, значит риск UNKNOWN, а не READY.
        assess(t.serverKey(), sid2)
                .andExpect(jsonPath("$.profileStatus").value("LEARNING"));
    }

    @Test
    void latestAssessmentIsReturnedForSession() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "latest-client");
        assess(t.serverKey(), sid).andExpect(status().isOk());
        mvc.perform(get("/api/v1/risk/" + sid).header("X-BG-Api-Key", t.serverKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sid));
    }

    @Test
    void otherOrganizationCannotAssessOrReadSession() throws Exception {
        Tenant a = newTenant();
        Tenant b = newTenant();
        String sid = createSession(a.sdkKey(), "tenant-client");
        assess(b.serverKey(), sid).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/risk/" + sid).header("X-BG-Api-Key", b.serverKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void sdkKeyCannotCallRiskApi() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "sdk-client");
        assess(t.sdkKey(), sid).andExpect(status().isForbidden());
    }

    @Test
    void missingKeyIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/risk/assess").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"00000000-0000-0000-0000-000000000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void historyKeepsEveryAssessmentNewestFirst() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "history-client");
        assess(t.serverKey(), sid).andExpect(status().isOk());
        assess(t.serverKey(), sid).andExpect(status().isOk());
        mvc.perform(get("/api/v1/risk/" + sid + "/history").header("X-BG-Api-Key", t.serverKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].riskLevel").value("UNKNOWN"));
    }
}
