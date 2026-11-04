package kz.behaviorguard.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kz.behaviorguard.backend.ratelimit.RateLimiter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Читает заголовок X-BG-Api-Key. Неверный или отсутствующий ключ просто не даёт
 * аутентификации: дальше решает политика доступа (ответ 401).
 * Не регистрируется как @Component, чтобы не попасть в servlet-цепочку дважды.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-BG-Api-Key";

    private final ApiKeyService apiKeyService;
    private final kz.behaviorguard.backend.ratelimit.RateLimiter limiter;
    private final kz.behaviorguard.backend.ratelimit.RateLimitConfig limits;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService,
                                      kz.behaviorguard.backend.ratelimit.RateLimiter limiter,
                                      kz.behaviorguard.backend.ratelimit.RateLimitConfig limits) {
        this.apiKeyService = apiKeyService;
        this.limiter = limiter;
        this.limits = limits;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<ApiKeyPrincipal> principal = apiKeyService.authenticate(request.getHeader(HEADER));
        if (principal.isPresent()) {
            ApiKeyPrincipal p0 = principal.get();
            String key = "api:" + p0.organizationId() + ":" + p0.keyType();
            long now = System.currentTimeMillis();
            // При сбое Redis API не блокируем (доступность важнее); сбой пишется в лог ограничителем.
            if (limiter.tryAcquire(key, limits.apiPerMinute(), now) == RateLimiter.Result.DENIED) {
                response.setHeader("Retry-After", String.valueOf(limiter.retryAfterSeconds(now)));
                response.sendError(429, "Превышен лимит запросов интеграции");
                return;
            }
        }
        principal.ifPresent(p -> {
            String role = p.keyType() == ApiKey.Type.PUBLIC_CLIENT ? "ROLE_SDK" : "ROLE_SERVER";
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority(role))));
        });
        chain.doFilter(request, response);
    }
}
