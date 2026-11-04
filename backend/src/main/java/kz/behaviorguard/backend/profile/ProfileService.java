package kz.behaviorguard.backend.profile;

import kz.behaviorguard.backend.events.BehavioralEventRepository;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.session.UserSession;
import kz.behaviorguard.backend.user.AnalyzedUser;
import kz.behaviorguard.backend.user.AnalyzedUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileService {

    /** Сколько завершённых сессий нужно, чтобы профиль считался готовым (READY). */
    public static final int READY_MIN_SESSIONS = 5;

    private final BehavioralProfileRepository profiles;
    private final BehavioralEventRepository events;
    private final AnalyzedUserRepository users;
    private final OrganizationRepository organizations;

    public ProfileService(BehavioralProfileRepository profiles,
                          BehavioralEventRepository events,
                          AnalyzedUserRepository users,
                          OrganizationRepository organizations) {
        this.profiles = profiles;
        this.events = events;
        this.users = users;
        this.organizations = organizations;
    }

    public static AnalyzedUser.ProfileStatus statusFor(int sessionCount) {
        if (sessionCount <= 0) return AnalyzedUser.ProfileStatus.NEW;
        if (sessionCount < READY_MIN_SESSIONS) return AnalyzedUser.ProfileStatus.LEARNING;
        return AnalyzedUser.ProfileStatus.READY;
    }

    /** Учитывает завершённую сессию в профиле пользователя. Вызывается один раз при завершении. */
    @Transactional
    public void learnFromSession(UserSession session) {
        UUID orgId = session.getOrganization().getId();
        var sessionEvents = events.findBySessionIdAndOrganizationId(session.getId(), orgId);
        var features = FeatureExtractor.extract(sessionEvents);
        if (FeatureExtractor.available(features) == 0) {
            return; // пустая сессия не меняет профиль
        }
        AnalyzedUser user = session.getUser();
        BehavioralProfile profile = profiles.findByOrganizationIdAndUserId(orgId, user.getId())
                .orElseGet(() -> profiles.save(new BehavioralProfile(
                        organizations.getReferenceById(orgId), user)));
        profile.learn(features);
        user.updateProfileStatus(statusFor(profile.getSessionCount()));
    }

    @Transactional(readOnly = true)
    public Optional<AnalyzedUser> findUser(UUID organizationId, UUID userId) {
        return users.findByIdAndOrganizationId(userId, organizationId);
    }

    @Transactional(readOnly = true)
    public Optional<BehavioralProfile> find(UUID organizationId, UUID userId) {
        return profiles.findByOrganizationIdAndUserId(organizationId, userId);
    }
}
