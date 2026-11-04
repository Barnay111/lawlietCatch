package kz.behaviorguard.backend.session;

import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.user.AnalyzedUser;
import kz.behaviorguard.backend.user.AnalyzedUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class SessionService {

    private final OrganizationRepository organizations;
    private final AnalyzedUserRepository users;
    private final UserSessionRepository sessions;
    private final kz.behaviorguard.backend.profile.ProfileService profiles;

    public SessionService(OrganizationRepository organizations,
                          AnalyzedUserRepository users,
                          UserSessionRepository sessions,
                          kz.behaviorguard.backend.profile.ProfileService profiles) {
        this.organizations = organizations;
        this.users = users;
        this.sessions = sessions;
        this.profiles = profiles;
    }

    /** Создаёт сессию внутри организации вызывающего; пользователь создаётся при первом появлении. */
    @Transactional
    public SessionResponse create(UUID organizationId, CreateSessionRequest request) {
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new IllegalStateException("Организация не найдена: " + organizationId));

        AnalyzedUser user = users.findByOrganizationIdAndExternalUserId(organizationId, request.userId())
                .orElseGet(() -> users.save(new AnalyzedUser(organization, request.userId())));

        UserSession session = sessions.save(new UserSession(
                organization, user, Instant.now(), request.deviceType(), request.sdkVersion()));
        return SessionResponse.from(session);
    }

    /** Завершает сессию своей организации. Идемпотентно. */
    @Transactional
    public SessionResponse finish(UUID organizationId, UUID sessionId) {
        UserSession session = sessions.findByIdAndOrganizationId(sessionId, organizationId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Сессия не найдена"));
        boolean wasActive = session.getStatus() == UserSession.Status.ACTIVE;
        session.finish(Instant.now());
        // Профиль учитывает сессию один раз: при переходе ACTIVE -> COMPLETED.
        if (wasActive) {
            profiles.learnFromSession(session);
        }
        return SessionResponse.from(session);
    }
}
