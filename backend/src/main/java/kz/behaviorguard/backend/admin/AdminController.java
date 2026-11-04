package kz.behaviorguard.backend.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.behaviorguard.backend.audit.AuditLog;
import kz.behaviorguard.backend.audit.AuditLogRepository;
import kz.behaviorguard.backend.audit.AuditService;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Только роль ADMIN: управление аналитиками и журнал аудита. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    public record CreateAnalystRequest(
            @NotBlank
            @Size(min = 3, max = 64)
            @Pattern(regexp = "^[a-z0-9._-]+$", message = "логин: строчные буквы, цифры, . _ -")
            String username,
            @NotBlank @Size(max = 200) String password) {
    }

    public record AnalystView(String id, String username, String role) {
    }

    public record AuditView(String createdAt, String actorType, String actorId, String action,
                            String target, Map<String, Object> details) {
    }

    private final AdminUserRepository admins;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AuditLogRepository auditLog;
    private final OrganizationRepository organizations;

    public AdminController(AdminUserRepository admins, PasswordEncoder encoder, AuditService audit,
                           AuditLogRepository auditLog, OrganizationRepository organizations) {
        this.admins = admins;
        this.encoder = encoder;
        this.audit = audit;
        this.auditLog = auditLog;
        this.organizations = organizations;
    }

    @PostMapping("/analysts")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalystView createAnalyst(@AuthenticationPrincipal AdminPrincipal actor,
                                     @Valid @RequestBody CreateAnalystRequest req) {
        try {
            PasswordPolicy.check(req.password());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        if (admins.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Логин уже занят");
        }
        AdminUser created = admins.save(new AdminUser(
                organizations.getReferenceById(actor.organizationId()),
                req.username(), encoder.encode(req.password()), AdminUser.Role.ANALYST));
        audit.record(actor.organizationId(), "ADMIN", actor.adminId().toString(),
                "ANALYST_CREATED", created.getId().toString(), Map.of("username", req.username()));
        return new AnalystView(created.getId().toString(), created.getUsername(), created.getRole().name());
    }

    @GetMapping("/audit")
    public List<AuditView> audit(@AuthenticationPrincipal AdminPrincipal actor,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        return auditLog.findByOrganizationIdOrderByCreatedAtDesc(
                        actor.organizationId(), PageRequest.of(Math.max(page, 0), safeSize, Sort.unsorted()))
                .map(AdminController::view).getContent();
    }

    private static AuditView view(AuditLog a) {
        return new AuditView(a.getCreatedAt().toString(), a.getActorType(), a.getActorId(),
                a.getAction(), a.getTarget(), a.getDetails());
    }
}
