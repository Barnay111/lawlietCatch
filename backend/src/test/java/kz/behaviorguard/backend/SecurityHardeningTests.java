package kz.behaviorguard.backend;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Регрессионные тесты по результатам проверки безопасности (security/attack_probes.py). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTests {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;

    private String sdkKey() {
        Organization org = organizations.save(new Organization("Hardening " + System.nanoTime()));
        return apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey();
    }

    @Test
    void sdkVersionWithInjectionPayloadIsRejected() throws Exception {
        mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", sdkKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"ok-user\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0' OR 1=1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedBodyIsRejectedBeforeParsing() throws Exception {
        String pad = "x".repeat(2 * 1024 * 1024);
        int code = mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", sdkKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"a\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\",\"pad\":\"" + pad + "\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(code).isIn(400, 413);
    }

    @Test
    void dashboardPageCarriesSecurityHeaders() throws Exception {
        mvc.perform(get("/dashboard/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("style-src 'self';")))
                .andExpect(header().string("Strict-Transport-Security", org.hamcrest.Matchers.containsString("max-age=31536000")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
