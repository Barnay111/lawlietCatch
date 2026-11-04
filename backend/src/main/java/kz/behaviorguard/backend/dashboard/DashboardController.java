package kz.behaviorguard.backend.dashboard;

import kz.behaviorguard.backend.admin.AdminPrincipal;
import kz.behaviorguard.backend.profile.ProfileResponse;
import kz.behaviorguard.backend.risk.RiskPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Дашборд банка: доступен ролям ADMIN и ANALYST. Просмотры карточек пишутся в аудит. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/overview")
    public DashboardDtos.Overview overview(@AuthenticationPrincipal AdminPrincipal actor,
                                           @RequestParam(defaultValue = "24") int hours) {
        if (hours < 1 || hours > 168) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hours: от 1 до 168");
        }
        return dashboard.overview(actor.organizationId(), hours);
    }

    @GetMapping("/assessments")
    public DashboardDtos.Page<DashboardDtos.AssessmentRow> assessments(
            @AuthenticationPrincipal AdminPrincipal actor,
            @RequestParam(required = false) RiskPolicy.RiskLevel level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return dashboard.feed(actor.organizationId(), level, page, size);
    }

    @GetMapping("/sessions/{id}")
    public DashboardDtos.SessionDetail session(@AuthenticationPrincipal AdminPrincipal actor,
                                               @PathVariable UUID id) {
        return dashboard.sessionDetail(actor, id);
    }

    @GetMapping("/users/{id}/profile")
    public ProfileResponse profile(@AuthenticationPrincipal AdminPrincipal actor, @PathVariable UUID id) {
        return dashboard.profile(actor, id);
    }
}
