package kz.behaviorguard.backend.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Токен администратора: из заголовка Authorization (API-клиенты) или из httpOnly-cookie (дашборд).
 * Запросы, которые меняют состояние и идут с cookie, требуют заголовок X-BG-Requested: защита от CSRF
 * поверх SameSite=Strict. Браузер не добавит такой заголовок в кросс-сайтовый запрос без CORS-предзапроса.
 */
public class BearerTokenFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER = "X-BG-Requested";

    private final AdminTokenRepository tokens;

    public BearerTokenFilter(AdminTokenRepository tokens) {
        this.tokens = tokens;
    }

    /** Токен из заголовка или cookie. Нужен и фильтру, и выходу. */
    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthController.COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean viaCookie = (header == null || !header.startsWith("Bearer "));
        String raw = extractToken(request);
        if (raw != null) {
            boolean unsafe = !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())
                    || "OPTIONS".equals(request.getMethod()));
            if (viaCookie && unsafe && !"dashboard".equals(request.getHeader(CSRF_HEADER))) {
                response.sendError(403, "CSRF: требуется заголовок " + CSRF_HEADER);
                return;
            }
            Instant now = Instant.now();
            tokens.findByTokenHash(AuthService.sha256(raw))
                    .filter(t -> t.isValid(now) && t.getAdmin().isUsable(now))
                    .ifPresent(t -> {
                        AdminUser a = t.getAdmin();
                        AdminPrincipal p = new AdminPrincipal(a.getId(), a.getOrganization().getId(),
                                a.getRole(), a.getUsername());
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(p, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + a.getRole().name()))));
                    });
        }
        chain.doFilter(request, response);
    }
}
