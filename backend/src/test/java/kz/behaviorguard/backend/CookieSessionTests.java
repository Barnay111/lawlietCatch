package kz.behaviorguard.backend;

import kz.behaviorguard.backend.admin.AdminUser;
import kz.behaviorguard.backend.admin.AdminUserRepository;
import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CookieSessionTests {

    private static final String PASSWORD = "correct-horse-42";

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository organizations;
    @Autowired AdminUserRepository admins;
    @Autowired PasswordEncoder encoder;

    private String newAdmin() {
        String suffix = Long.toString(System.nanoTime());
        Organization org = organizations.save(new Organization("Cookie Bank " + suffix));
        String user = "cookie-" + suffix;
        admins.save(new AdminUser(org, user, encoder.encode(PASSWORD), AdminUser.Role.ADMIN));
        return user;
    }

    private MvcResult login(String user) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    private static Cookie cookieFrom(MvcResult r) {
        Cookie c = r.getResponse().getCookie("bg_admin");
        assertThat(c).isNotNull();
        return c;
    }

    @Test
    void loginSetsHttpOnlyStrictCookie() throws Exception {
        MvcResult r = login(newAdmin());
        String setCookie = r.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("bg_admin=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/");
    }

    @Test
    void cookieAuthenticatesDashboardRequests() throws Exception {
        Cookie c = cookieFrom(login(newAdmin()));
        mvc.perform(get("/api/v1/auth/me").cookie(c))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/overview?hours=24").cookie(c))
                .andExpect(status().isOk());
    }

    @Test
    void meWithoutSessionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieAuthChangingRequestNeedsCsrfHeader() throws Exception {
        Cookie c = cookieFrom(login(newAdmin()));
        String body = "{\"username\":\"csrf-" + System.nanoTime() + "\",\"password\":\"long-enough-pass-1\"}";
        // Без заголовка: отказ, даже если cookie валидна (поддельный кросс-сайтовый запрос).
        mvc.perform(post("/api/v1/admin/analysts").cookie(c)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        // С заголовком дашборда: запрос проходит.
        mvc.perform(post("/api/v1/admin/analysts").cookie(c).header("X-BG-Requested", "dashboard")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void logoutClearsCookieAndRevokesToken() throws Exception {
        Cookie c = cookieFrom(login(newAdmin()));
        MvcResult out = mvc.perform(post("/api/v1/auth/logout").cookie(c).header("X-BG-Requested", "dashboard"))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        mvc.perform(get("/api/v1/dashboard/overview").cookie(c)).andExpect(status().isUnauthorized());
    }
}
