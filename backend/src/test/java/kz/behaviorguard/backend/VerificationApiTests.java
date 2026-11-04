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
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class VerificationApiTests {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiKeyService apiKeys;

    private record Keys(String sdk, String server) {
    }

    private Keys keys() {
        Organization org = organizations.save(new Organization("Verify Bank " + System.nanoTime()));
        return new Keys(apiKeys.issue(org, ApiKey.Type.PUBLIC_CLIENT).rawKey(),
                apiKeys.issue(org, ApiKey.Type.SERVER).rawKey());
    }

    private String session(Keys k, String userId) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/sessions").header("X-BG-Api-Key", k.sdk())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"deviceType\":\"MOBILE\",\"sdkVersion\":\"0.1.0\"}"))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(r.getResponse().getContentAsString());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    void newClientWithoutProfileIsNotPassedSilently() throws Exception {
        Keys k = keys();
        String sid = session(k, "verify-new");
        mvc.perform(post("/api/v1/verification/decide").header("X-BG-Api-Key", k.server())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\",\"context\":{\"action\":\"TRANSFER\",\"amount\":5000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("CONFIRM"))
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.reasons[0]").value("INSUFFICIENT_DATA"));
    }

    @Test
    void sdkKeyCannotDecide() throws Exception {
        Keys k = keys();
        String sid = session(k, "verify-sdk");
        mvc.perform(post("/api/v1/verification/decide").header("X-BG-Api-Key", k.sdk())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherOrganizationCannotDecideForForeignSession() throws Exception {
        Keys a = keys();
        Keys b = keys();
        String sid = session(a, "verify-foreign");
        mvc.perform(post("/api/v1/verification/decide").header("X-BG-Api-Key", b.server())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void negativeAmountIsRejected() throws Exception {
        Keys k = keys();
        String sid = session(k, "verify-neg");
        mvc.perform(post("/api/v1/verification/decide").header("X-BG-Api-Key", k.server())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sid + "\",\"context\":{\"action\":\"TRANSFER\",\"amount\":-5}}"))
                .andExpect(status().isBadRequest());
    }
}
