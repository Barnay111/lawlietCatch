package kz.behaviorguard.backend;

import kz.behaviorguard.backend.admin.AdminUser;
import kz.behaviorguard.backend.admin.AdminUserRepository;
import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.security.ApiKey;
import kz.behaviorguard.backend.security.ApiKeyService;
import kz.behaviorguard.backend.user.AnalyzedUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ErasureTests {

    private static final String PASSWORD = "correct-horse-42";

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;
    @Autowired AdminUserRepository admins;
    @Autowired AnalyzedUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    private record Bank(UUID orgId, String sdkKey, String serverKey, String adminToken, String analystToken) {
    }

    private Bank newBank() throws Exception {
        String s = Long.toString(System.nanoTime());
        Organization org = organizations.save(new Organization("Erasure Bank " + s));
        String admin = "era-admin-" + s;
        String analyst = "era-analyst-" + s;
        admins.save(new AdminUser(org, admin, encoder.encode(PASSWORD), AdminUser.Role.ADMIN));
        admins.save(new AdminUser(org, analyst, encoder.encode(PASSWORD), AdminUser.Role.ANALYST));
        return new Bank(org.getId(),
                apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey(),
                apiKeys.issue(org, ApiKey.Type.SERVER).rawKey(),
                login(admin), login(analyst));
    }

    private String login(String user) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    /** Клиент с сессией, событиями и оценкой риска. */
    private void clientWithData(Bank b, String userId) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", b.sdkKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        String sid = m.group(1);
        String now = Instant.now().toString();
        mvc.perform(post("/api/v1/events/batch").header("X-BG-Api-Key", b.sdkKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\",\"events\":[{\"eventType\":\"TAP\",\"eventTime\":\""
                                + now + "\",\"payload\":{\"durationMs\":120}}]}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/risk/assess").header("X-BG-Api-Key", b.serverKey())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sessionId\":\"" + sid + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/sessions/" + sid + "/finish").header("X-BG-Api-Key", b.sdkKey()))
                .andExpect(status().isOk());
    }

    private static final String REASON = "Запрос клиента на удаление данных";

    @Test
    void onlyAdminCanErase() throws Exception {
        Bank b = newBank();
        clientWithData(b, "erase-role");
        mvc.perform(post("/api/v1/admin/erasure").header("Authorization", "Bearer " + b.analystToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"erase-role\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void erasureRemovesAllUserDataAndLeavesNoPseudonymInAudit() throws Exception {
        Bank b = newBank();
        clientWithData(b, "erase-me-42");
        assertThat(users.findByOrganizationIdAndExternalUserId(b.orgId(), "erase-me-42")).isPresent();

        mvc.perform(post("/api/v1/admin/erasure").header("Authorization", "Bearer " + b.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"erase-me-42\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted.sessions").value(1))
                .andExpect(jsonPath("$.deleted.riskAssessments").value(1))
                .andExpect(jsonPath("$.deleted.users").value(1));

        assertThat(users.findByOrganizationIdAndExternalUserId(b.orgId(), "erase-me-42")).isEmpty();
        Integer events = jdbc.queryForObject(
                "select count(*) from behavioral_events where organization_id = ?::uuid", Integer.class, b.orgId().toString());
        assertThat(events).isZero();

        String audit = mvc.perform(get("/api/v1/admin/audit?size=50").header("Authorization", "Bearer " + b.adminToken()))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("USER_DATA_ERASED").doesNotContain("erase-me-42");
    }

    @Test
    void otherOrganizationCannotEraseTheSameExternalId() throws Exception {
        Bank a = newBank();
        Bank other = newBank();
        clientWithData(a, "shared-ext-id");
        mvc.perform(post("/api/v1/admin/erasure").header("Authorization", "Bearer " + other.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"shared-ext-id\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(users.findByOrganizationIdAndExternalUserId(a.orgId(), "shared-ext-id")).isPresent();
    }

    @Test
    void shortReasonIsRejected() throws Exception {
        Bank b = newBank();
        mvc.perform(post("/api/v1/admin/erasure").header("Authorization", "Bearer " + b.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"x\",\"reason\":\"коротко\"}"))
                .andExpect(status().isBadRequest());
    }
}
