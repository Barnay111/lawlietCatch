package kz.behaviorguard.backend.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * IP клиента. X-Forwarded-For учитывается только если соединение пришло от доверенного прокси.
 * Иначе клиент мог бы подменить заголовок и обойти лимит входа.
 */
@Component
public class ClientIpResolver {

    private final Set<String> trustedProxies;

    public ClientIpResolver(@Value("${behaviorguard.trusted-proxies:}") String trusted) {
        this.trustedProxies = trusted.isBlank() ? Set.of()
                : Set.copyOf(Arrays.stream(trusted.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) {
            return remote;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remote;
        }
        // Идём справа налево: первый адрес, которому не доверяем, — это клиент.
        List<String> hops = Arrays.stream(xff.split(",")).map(String::trim).toList();
        for (int i = hops.size() - 1; i >= 0; i--) {
            if (!trustedProxies.contains(hops.get(i))) {
                return hops.get(i);
            }
        }
        return remote;
    }
}
