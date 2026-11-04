package kz.behaviorguard.backend;

import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.security.ApiKey;
import kz.behaviorguard.backend.security.ApiKeyService;
import kz.behaviorguard.backend.session.UserSessionRepository;
import kz.behaviorguard.backend.user.AnalyzedUserRepository;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SessionApiTests {

    private static final String BODY = """
            {"userId":"user-42","deviceType":"DESKTOP","sdkVersion":"0.1.0"}
            """;

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;
    @Autowired AnalyzedUserRepository users;
    @Autowired UserSessionRepository sessions;

    private ApiKeyService.IssuedKey newOrgKey(ApiKey.Type type) {
        Organization org = organizations.save(new Organization("Test Bank " + System.nanoTime()));
        return apiKeys.issue(org, type);
    }

    private ResultActions createSession(String rawKey, String body) throws Exception {
        var req = post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content(body);
        if (rawKey != null) {
            req.header("X-BG-Api-Key", rawKey);
        }
        return mvc.perform(req);
    }

    @Test
    void sdkKeyCreatesSession() throws Exception {
        var sdk = newOrgKey(ApiKey.Type.PUBLIC_CLIENT);

        createSession(sdk.rawKey(), BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value("user-42"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void sameUserIsReusedWithinOrganization() throws Exception {
        var sdk = newOrgKey(ApiKey.Type.PUBLIC_CLIENT);
        UUID orgId = sdk.entity().getOrganization().getId();

        createSession(sdk.rawKey(), BODY).andExpect(status().isCreated());
        createSession(sdk.rawKey(), BODY).andExpect(status().isCreated());

        assertThat(users.findByOrganizationIdAndExternalUserId(orgId, "user-42")).isPresent();
        assertThat(sessions.findAll().stream()
                .filter(s -> s.getOrganization().getId().equals(orgId)).count()).isEqualTo(2);
    }

    @Test
    void sameExternalIdInOtherOrganizationIsSeparateUser() throws Exception {
        var bankA = newOrgKey(ApiKey.Type.PUBLIC_CLIENT);
        var bankB = newOrgKey(ApiKey.Type.PUBLIC_CLIENT);

        createSession(bankA.rawKey(), BODY).andExpect(status().isCreated());
        createSession(bankB.rawKey(), BODY).andExpect(status().isCreated());

        var a = users.findByOrganizationIdAndExternalUserId(bankA.entity().getOrganization().getId(), "user-42");
        var b = users.findByOrganizationIdAndExternalUserId(bankB.entity().getOrganization().getId(), "user-42");
        assertThat(a).isPresent();
        assertThat(b).isPresent();
        assertThat(a.get().getId()).isNotEqualTo(b.get().getId());
    }

    @Test
    void missingKeyIsUnauthorized() throws Exception {
        createSession(null, BODY).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownKeyIsUnauthorized() throws Exception {
        createSession("bg_pk_not-a-real-key", BODY).andExpect(status().isUnauthorized());
    }

    @Test
    void serverKeyCannotCreateSession() throws Exception {
        var server = newOrgKey(ApiKey.Type.SERVER);
        createSession(server.rawKey(), BODY).andExpect(status().isForbidden());
    }

    @Test
    void emailInUserIdIsRejected() throws Exception {
        var sdk = newOrgKey(ApiKey.Type.PUBLIC_CLIENT);
        createSession(sdk.rawKey(), """
                {"userId":"ivan@mail.kz","deviceType":"MOBILE","sdkVersion":"0.1.0"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    void invalidDeviceTypeIsRejected() throws Exception {
        var sdk = newOrgKey(ApiKey.Type.PUBLIC_CLIENT);
        createSession(sdk.rawKey(), """
                {"userId":"user-1","deviceType":"TV","sdkVersion":"0.1.0"}
                """).andExpect(status().isBadRequest());
    }
}
