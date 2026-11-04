package kz.behaviorguard.backend;

import kz.behaviorguard.backend.events.BehavioralEventRepository;
import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.security.ApiKey;
import kz.behaviorguard.backend.security.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EventApiTests {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;
    @Autowired BehavioralEventRepository events;

    private String sdkKey;

    private String newSdkKey() {
        Organization org = organizations.save(new Organization("Events Bank " + System.nanoTime()));
        return apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey();
    }

    private String createSession(String key) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"u-1\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions batch(String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/events/batch").header("X-BG-Api-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String event(String type, String payload) {
        return "{\"eventType\":\"" + type + "\",\"eventTime\":\"" + Instant.now() + "\",\"payload\":" + payload + "}";
    }

    private static String batchOf(String sessionId, String... events) {
        return "{\"sessionId\":\"" + sessionId + "\",\"events\":[" + String.join(",", events) + "]}";
    }

    @Test
    void validMobileSignalsAreAccepted() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        batch(sdkKey, batchOf(sid,
                event("TAP", "{\"durationMs\":120,\"intervalMs\":450}"),
                event("SWIPE", "{\"distancePx\":300,\"durationMs\":180,\"avgSpeedPxS\":1666,\"direction\":\"LEFT\"}"),
                event("TEXT_INPUT", "{\"fieldId\":\"transfer.amount\",\"keyCount\":12,\"keyIntervalAvgMs\":210,"
                        + "\"keyIntervalStdMs\":40,\"charsPerMinute\":180,\"backspaceCount\":1}"),
                event("SCREEN_VIEW", "{\"screen\":\"transfer\",\"dwellMs\":5400}")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(4));
    }

    @Test
    void unknownEventTypeIsRejected() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        batch(sdkKey, batchOf(sid, event("KEYSTROKE_CONTENT", "{\"durationMs\":1}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPayloadFieldIsRejected() throws Exception {
        // Попытка протащить содержимое поля: поля value / text в allowlist нет.
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        batch(sdkKey, batchOf(sid,
                event("TEXT_INPUT", "{\"fieldId\":\"transfer.card\",\"keyCount\":3,\"keyIntervalAvgMs\":100,"
                        + "\"keyIntervalStdMs\":10,\"charsPerMinute\":150,\"backspaceCount\":0,\"value\":\"4400 1234\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stringWhereNumberExpectedIsRejected() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        batch(sdkKey, batchOf(sid, event("TAP", "{\"durationMs\":\"120ms\"}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outOfRangeValueIsRejected() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        batch(sdkKey, batchOf(sid, event("TAP", "{\"durationMs\":999999}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fieldIdWithEmailIsRejected() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        batch(sdkKey, batchOf(sid, event("PASTE", "{\"fieldId\":\"ivan@mail.kz\",\"pasteCount\":1}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void futureTimestampIsRejected() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        String future = "{\"eventType\":\"IDLE\",\"eventTime\":\"" + Instant.now().plusSeconds(3600)
                + "\",\"payload\":{\"durationMs\":1000}}";
        batch(sdkKey, batchOf(sid, future)).andExpect(status().isBadRequest());
    }

    @Test
    void wholeBatchIsRejectedIfOneEventIsInvalid() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        long before = events.count();
        batch(sdkKey, batchOf(sid,
                event("TAP", "{\"durationMs\":100}"),
                event("TAP", "{\"durationMs\":-5}")))
                .andExpect(status().isBadRequest());
        assertThat(events.count()).isEqualTo(before);
    }

    @Test
    void sessionFromAnotherOrganizationIsNotFound() throws Exception {
        String bankA = newSdkKey();
        String bankB = newSdkKey();
        String sidA = createSession(bankA);
        batch(bankB, batchOf(sidA, event("TAP", "{\"durationMs\":100}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void finishedSessionRejectsEventsAndFinishIsIdempotent() throws Exception {
        sdkKey = newSdkKey();
        String sid = createSession(sdkKey);
        mvc.perform(post("/api/v1/sessions/" + sid + "/finish").header("X-BG-Api-Key", sdkKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post("/api/v1/sessions/" + sid + "/finish").header("X-BG-Api-Key", sdkKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        batch(sdkKey, batchOf(sid, event("TAP", "{\"durationMs\":100}")))
                .andExpect(status().isConflict());
    }
}
