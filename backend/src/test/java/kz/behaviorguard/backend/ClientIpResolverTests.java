package kz.behaviorguard.backend;

import kz.behaviorguard.backend.ratelimit.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTests {

    private static MockHttpServletRequest from(String remote, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void untrustedClientCannotSpoofForwardedFor() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1");
        assertThat(resolver.resolve(from("198.51.100.7", "1.2.3.4"))).isEqualTo("198.51.100.7");
    }

    @Test
    void trustedProxyPassesRealClientFromRightmostUntrustedHop() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.1,10.0.0.2");
        assertThat(resolver.resolve(from("10.0.0.1", "203.0.113.5, 10.0.0.2"))).isEqualTo("203.0.113.5");
    }

    @Test
    void noTrustedProxiesMeansRemoteAddressIsUsed() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.resolve(from("10.0.0.1", "203.0.113.5"))).isEqualTo("10.0.0.1");
    }
}
