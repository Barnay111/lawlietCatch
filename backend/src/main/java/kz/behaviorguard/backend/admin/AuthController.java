package kz.behaviorguard.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kz.behaviorguard.backend.ratelimit.ClientIpResolver;
import kz.behaviorguard.backend.ratelimit.RateLimitConfig;
import kz.behaviorguard.backend.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String COOKIE = "bg_admin";

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 200) String password) {
    }

    private final AuthService auth;
    private final RateLimiter limiter;
    private final RateLimitConfig limits;
    private final boolean cookieSecure;
    private final ClientIpResolver clientIp;

    public AuthController(AuthService auth, RateLimiter limiter, RateLimitConfig limits,
                          ClientIpResolver clientIp,
                          @Value("${behaviorguard.cookie.secure:false}") boolean cookieSecure) {
        this.auth = auth;
        this.limiter = limiter;
        this.limits = limits;
        this.clientIp = clientIp;
        this.cookieSecure = cookieSecure;
    }

    /**
     * Токен уходит в httpOnly-cookie: страница дашборда до него не дотягивается из JavaScript.
     * В теле ответа токен тоже есть для API-клиентов, которые работают через заголовок Authorization.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        long now = System.currentTimeMillis();
        RateLimiter.Result r = limiter.tryAcquire("login:" + clientIp.resolve(request), limits.loginPerMinute(), now);
        if (r == RateLimiter.Result.DENIED) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(limiter.retryAfterSeconds(now)))
                    .body(Map.of("error", "Слишком много попыток входа. Повторите позже."));
        }
        if (r == RateLimiter.Result.UNAVAILABLE) {
            // Вход без работающего ограничителя не пускаем: иначе перебор откроется при сбое Redis.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Сервис временно недоступен. Повторите позже."));
        }
        return switch (auth.login(req.username(), req.password())) {
            case AuthService.Success s -> ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, sessionCookie(s.token(), Duration.ofHours(8)).toString())
                    .body(Map.of(
                            "accessToken", s.token(),
                            "tokenType", "Bearer",
                            "expiresAt", s.expiresAt().toString(),
                            "role", s.role().name(),
                            "username", s.username()));
            case AuthService.Locked l -> ResponseEntity.status(HttpStatus.LOCKED)
                    .body(Map.of("error", "Слишком много неудачных попыток. Повторите позже."));
            case AuthService.Failed f -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверный логин или пароль"));
        };
    }

    /** Кто вошёл: страница дашборда проверяет сессию при загрузке. */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AdminPrincipal actor) {
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("role", actor.role().name(), "username", actor.username()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AdminPrincipal actor,
                                       HttpServletRequest request) {
        String token = BearerTokenFilter.extractToken(request);
        if (token != null) {
            auth.logout(token);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
