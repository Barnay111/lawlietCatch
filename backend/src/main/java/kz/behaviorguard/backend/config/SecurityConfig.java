package kz.behaviorguard.backend.config;

import kz.behaviorguard.backend.admin.AdminTokenRepository;
import kz.behaviorguard.backend.admin.BearerTokenFilter;
import kz.behaviorguard.backend.security.ApiKeyAuthenticationFilter;
import kz.behaviorguard.backend.security.ApiKeyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Политика безопасности API:
 * - health открыт;
 * - создание сессии доступно только ключу SDK (ROLE_SDK);
 * - всё остальное закрыто по умолчанию;
 * - без действующего ключа ответ 401, с ключом без нужной роли — 403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyService apiKeyService,
                                            AdminTokenRepository adminTokens,
                                            kz.behaviorguard.backend.ratelimit.RateLimiter limiter,
                                            kz.behaviorguard.backend.ratelimit.RateLimitConfig limits) throws Exception {
        http
            .addFilterBefore(new BodySizeLimitFilter(1024 * 1024), SecurityContextHolderFilter.class)
            // Заголовки для страницы дашборда. Inline-стилей нет: высоты столбцов задаются через DOM.
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; "
                        + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; "
                        + "base-uri 'self'; form-action 'self'"))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)
                        .requestMatcher(org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE))
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .csrf(csrf -> csrf.disable())            // stateless API, без cookie-сессий
            .sessionManagement(s -> s.disable())
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService, limiter, limits),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new BearerTokenFilter(adminTokens),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Отрисовка ошибок валидации (400): без этого Spring пересылает ответ
                // на /error, он закрыт, и клиент получает 401 вместо 400.
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/sessions", "/api/v1/sessions/*/finish",
                        "/api/v1/events/batch").hasRole("SDK")
                // Risk API и профили вызывает бэкенд банка по серверному ключу.
                .requestMatchers("/api/v1/risk/**", "/api/v1/users/*/profile").hasRole("SERVER")
                // Вход в дашборд открыт; токен выдаётся только после проверки пароля.
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Статика дашборда открыта: данные по API закрыты токеном.
                .requestMatchers("/dashboard/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/dashboard/**").hasAnyRole("ADMIN", "ANALYST")
                .anyRequest().denyAll());
        return http.build();
    }
}
