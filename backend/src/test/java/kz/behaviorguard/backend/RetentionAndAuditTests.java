package kz.behaviorguard.backend;

import kz.behaviorguard.backend.events.RawEventRetention;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RetentionAndAuditTests {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;
    @Autowired JdbcTemplate jdbc;
    @Autowired RawEventRetention retention;

    @Test
    void rawEventsOlderThanRetentionArePurgedAndNewOnesKept() throws Exception {
        Organization org = organizations.save(new Organization("Retention " + System.nanoTime()));
        String key = apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey();
        MvcResult r = mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ret-user\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        String sid = m.group(1);
        String now = Instant.now().toString();
        mvc.perform(post("/api/v1/events/batch").header("X-BG-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\",\"events\":["
                                + "{\"eventType\":\"IDLE\",\"eventTime\":\"" + now + "\",\"payload\":{\"durationMs\":1000}},"
                                + "{\"eventType\":\"IDLE\",\"eventTime\":\"" + now + "\",\"payload\":{\"durationMs\":2000}}]}"))
                .andExpect(status().isAccepted());

        // Одно событие «старое»: принято 200 дней назад.
        jdbc.update("update behavioral_events set received_at = now() - interval '200 days' "
                + "where session_id = ?::uuid and payload->>'durationMs' = '1000'", sid);

        long deleted = retention.purge(Instant.now().plus(0, ChronoUnit.SECONDS));
        assertThat(deleted).isGreaterThanOrEqualTo(1);
        Integer left = jdbc.queryForObject(
                "select count(*) from behavioral_events where session_id = ?::uuid", Integer.class, sid);
        assertThat(left).isEqualTo(1);
    }

    @Test
    void auditLogCannotBeUpdatedOrDeleted() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from audit_log", Integer.class);
        assertThat(before).isNotNull();
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'TAMPERED'"))
                .hasMessageContaining("запрещены");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log"))
                .hasMessageContaining("запрещены");
        assertThatThrownBy(() -> jdbc.execute("truncate audit_log"))
                .hasMessageContaining("запрещены");
    }
}
