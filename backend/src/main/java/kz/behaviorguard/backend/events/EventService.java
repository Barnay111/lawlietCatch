package kz.behaviorguard.backend.events;

import kz.behaviorguard.backend.organization.Organization;
import kz.behaviorguard.backend.organization.OrganizationRepository;
import kz.behaviorguard.backend.session.UserSession;
import kz.behaviorguard.backend.session.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

    /** Допустимый перекос часов клиента относительно сервера. */
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final OrganizationRepository organizations;
    private final UserSessionRepository sessions;
    private final BehavioralEventRepository events;

    public EventService(OrganizationRepository organizations,
                        UserSessionRepository sessions,
                        BehavioralEventRepository events) {
        this.organizations = organizations;
        this.sessions = sessions;
        this.events = events;
    }

    @Transactional
    public EventBatchResponse acceptBatch(UUID organizationId, EventBatchRequest request) {
        UserSession session = sessions.findByIdAndOrganizationId(request.sessionId(), organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена"));
        if (session.getStatus() != UserSession.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сессия уже завершена");
        }

        Instant now = Instant.now();
        List<String> errors = new ArrayList<>();
        List<BehavioralEvent> toSave = new ArrayList<>(request.events().size());
        Organization organization = organizations.getReferenceById(organizationId);

        for (int i = 0; i < request.events().size(); i++) {
            EventIn in = request.events().get(i);
            if (in.eventTime().isAfter(now.plus(MAX_CLOCK_SKEW))) {
                errors.add("событие " + i + ": время из будущего");
                continue;
            }
            for (String v : in.eventType().violations(in.payload())) {
                errors.add("событие " + i + " (" + in.eventType() + "): " + v);
            }
            toSave.add(new BehavioralEvent(organization, session, in.eventType(), in.eventTime(), in.payload()));
        }

        // Батч либо принимается целиком, либо отклоняется целиком: частично принятые данные
        // сломали бы расчёт признаков для сессии.
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }

        events.saveAll(toSave);
        session.addEvents(toSave.size());
        return new EventBatchResponse(toSave.size());
    }
}
