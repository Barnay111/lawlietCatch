package kz.behaviorguard.backend.events;

import jakarta.validation.Valid;
import kz.behaviorguard.backend.security.ApiKeyPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventBatchResponse batch(@AuthenticationPrincipal ApiKeyPrincipal principal,
                                    @Valid @RequestBody EventBatchRequest request) {
        return eventService.acceptBatch(principal.organizationId(), request);
    }
}
