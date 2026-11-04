package kz.behaviorguard.backend.profile;

import kz.behaviorguard.backend.security.ApiKeyPrincipal;
import kz.behaviorguard.backend.user.AnalyzedUser;
import kz.behaviorguard.backend.user.AnalyzedUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class ProfileController {

    private final AnalyzedUserRepository users;
    private final ProfileService profiles;

    public ProfileController(AnalyzedUserRepository users, ProfileService profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @GetMapping("/{id}/profile")
    public ProfileResponse profile(@AuthenticationPrincipal ApiKeyPrincipal principal, @PathVariable UUID id) {
        AnalyzedUser user = users.findByIdAndOrganizationId(id, principal.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
        return ProfileResponse.of(user.getId(), profiles.find(principal.organizationId(), user.getId()).orElse(null));
    }
}
