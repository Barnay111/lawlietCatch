package kz.behaviorguard.backend;

import kz.behaviorguard.backend.admin.AdminUser;
import kz.behaviorguard.backend.admin.AdminUserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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
class DashboardApiTests {

    private static final String PASSWORD = "correct-horse-42";

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;
    @Autowired AdminUserRepository admins;
    @Autowired PasswordEncoder encoder;

    private record Tenant(UUID orgId, String sdkKey, String serverKey, String adminToken, String analystToken,
                          String adminUser, String analystUser) {
    }

    private Tenant newTenant() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        Organization org = organizations.save(new Organization("Dash Bank " + suffix));
        String adminUser = "admin-" + suffix;
        String analystUser = "analyst-" + suffix;
        admins.save(new AdminUser(org, adminUser, encoder.encode(PASSWORD), AdminUser.Role.ADMIN));
        admins.save(new AdminUser(org, analystUser, encoder.encode(PASSWORD), AdminUser.Role.ANALYST));
        Tenant base = new Tenant(org.getId(),
                apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey(),
                apiKeys.issue(org, ApiKey.Type.SERVER).rawKey(),
                login(adminUser, PASSWORD), login(analystUser, PASSWORD), adminUser, analystUser);
        return base;
    }

    private String login(String user, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private ResultActions loginAttempt(String user, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"));
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

    /** Без профиля риск уходит в UNKNOWN: оценка появляется в ленте без моделей. */
    private void unknownAssessment(Tenant t, String sid) throws Exception {
        mvc.perform(post("/api/v1/risk/assess").header("X-BG-Api-Key", t.serverKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions bearer(String method, String path, String token) throws Exception {
        var req = method.equals("GET") ? get(path) : post(path);
        return mvc.perform(req.header("Authorization", "Bearer " + token));
    }

    @Test
    void wrongPasswordIsUnauthorizedAndRecordedInAudit() throws Exception {
        Tenant t = newTenant();
        loginAttempt(t.adminUser(), "wrong-password-1").andExpect(status().isUnauthorized());
        String audit = bearer("GET", "/api/v1/admin/audit", t.adminToken()).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("LOGIN_FAILED");
    }

    @Test
    void fiveFailuresLockAccountEvenWithCorrectPassword() throws Exception {
        Tenant t = newTenant();
        for (int i = 0; i < 5; i++) {
            loginAttempt(t.analystUser(), "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        loginAttempt(t.analystUser(), PASSWORD).andExpect(status().isLocked());
    }

    @Test
    void dashboardRequiresBearerToken() throws Exception {
        Tenant t = newTenant();
        mvc.perform(get("/api/v1/dashboard/overview")).andExpect(status().isUnauthorized());
        // Ключ интеграции аутентифицирует, но роли дашборда у него нет
        mvc.perform(get("/api/v1/dashboard/overview").header("X-BG-Api-Key", t.sdkKey()))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystReadsDashboardButCannotManageAdmins() throws Exception {
        Tenant t = newTenant();
        bearer("GET", "/api/v1/dashboard/overview", t.analystToken()).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/analysts").header("Authorization", "Bearer " + t.analystToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-one\",\"password\":\"long-enough-pass-1\"}"))
                .andExpect(status().isForbidden());
        bearer("GET", "/api/v1/admin/audit", t.analystToken()).andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAnalystWhoCanLogIn() throws Exception {
        Tenant t = newTenant();
        String name = "rep-" + System.nanoTime();
        mvc.perform(post("/api/v1/admin/analysts").header("Authorization", "Bearer " + t.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"long-enough-pass-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ANALYST"));
        login(name, "long-enough-pass-1");
    }

    @Test
    void weakPasswordForNewAnalystIsRejected() throws Exception {
        Tenant t = newTenant();
        mvc.perform(post("/api/v1/admin/analysts").header("Authorization", "Bearer " + t.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak-" + System.nanoTime() + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overviewCountsSessionsAndAssessments() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "dash-client");
        unknownAssessment(t, sid);
        bearer("GET", "/api/v1/dashboard/overview?hours=24", t.analystToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionsStarted").value(1))
                .andExpect(jsonPath("$.assessedSessions").value(1))
                .andExpect(jsonPath("$.unknown").value(1))
                .andExpect(jsonPath("$.levels.UNKNOWN").value(1))
                .andExpect(jsonPath("$.recent[0].sessionId").value(sid));
    }

    @Test
    void overviewRejectsOutOfRangeWindow() throws Exception {
        Tenant t = newTenant();
        bearer("GET", "/api/v1/dashboard/overview?hours=500", t.analystToken()).andExpect(status().isBadRequest());
    }

    @Test
    void feedFiltersByLevel() throws Exception {
        Tenant t = newTenant();
        unknownAssessment(t, createSession(t.sdkKey(), "feed-client"));
        bearer("GET", "/api/v1/dashboard/assessments?level=UNKNOWN", t.analystToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        bearer("GET", "/api/v1/dashboard/assessments?level=HIGH", t.analystToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sessionDetailIsAuditedAndTenantScoped() throws Exception {
        Tenant a = newTenant();
        Tenant b = newTenant();
        String sid = createSession(a.sdkKey(), "scoped-client");
        bearer("GET", "/api/v1/dashboard/sessions/" + sid, a.analystToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("scoped-client"));
        bearer("GET", "/api/v1/dashboard/sessions/" + sid, b.adminToken()).andExpect(status().isNotFound());
        String audit = bearer("GET", "/api/v1/admin/audit", a.adminToken()).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("SESSION_VIEW");
        String auditB = bearer("GET", "/api/v1/admin/audit", b.adminToken()).andReturn().getResponse().getContentAsString();
        assertThat(auditB).doesNotContain(sid);
    }

    @Test
    void profileViewIsTenantScoped() throws Exception {
        Tenant a = newTenant();
        Tenant b = newTenant();
        String sid = createSession(a.sdkKey(), "profile-client");
        String userId = bearer("GET", "/api/v1/dashboard/sessions/" + sid, a.adminToken())
                .andReturn().getResponse().getContentAsString();
        assertThat(userId).contains("profile-client");
        UUID internalUserId = users().findByOrganizationIdAndExternalUserId(a.orgId(), "profile-client")
                .orElseThrow().getId();
        bearer("GET", "/api/v1/dashboard/users/" + internalUserId + "/profile", a.analystToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileStatus").value("NEW"));
        bearer("GET", "/api/v1/dashboard/users/" + internalUserId + "/profile", b.analystToken())
                .andExpect(status().isNotFound());
    }

    @Test
    void logoutRevokesToken() throws Exception {
        Tenant t = newTenant();
        String token = login(t.analystUser(), PASSWORD);
        bearer("GET", "/api/v1/dashboard/overview", token).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        bearer("GET", "/api/v1/dashboard/overview", token).andExpect(status().isUnauthorized());
    }

    @Autowired kz.behaviorguard.backend.user.AnalyzedUserRepository usersRepo;

    private kz.behaviorguard.backend.user.AnalyzedUserRepository users() {
        return usersRepo;
    }

    @Test
    void overviewCountsEachSessionOnceByLatestAssessment() throws Exception {
        Tenant t = newTenant();
        String sid = createSession(t.sdkKey(), "twice-client");
        unknownAssessment(t, sid);
        unknownAssessment(t, sid);
        bearer("GET", "/api/v1/dashboard/overview?hours=24", t.analystToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessedSessions").value(1))
                .andExpect(jsonPath("$.levels.UNKNOWN").value(1))
                .andExpect(jsonPath("$.timeline[0].counts.UNKNOWN").value(2))
                .andExpect(jsonPath("$.recent.length()").value(2));
    }
}
