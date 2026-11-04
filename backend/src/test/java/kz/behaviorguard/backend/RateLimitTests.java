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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Лимиты заданы маленькими, чтобы проверка укладывалась в одну минуту окна. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "behaviorguard.ratelimit.login-per-minute=10",
        "behaviorguard.ratelimit.api-per-minute=5"})
@AutoConfigureMockMvc
class RateLimitTests {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;

    @Test
    void loginIsLimitedPerIpAndAnswers429WithRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void integrationKeyIsLimitedAndAnswers429() throws Exception {
        Organization org = organizations.save(new Organization("RateLimit Bank " + System.nanoTime()));
        String key = apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey();
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"rl-" + i + "\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"rl-over\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
