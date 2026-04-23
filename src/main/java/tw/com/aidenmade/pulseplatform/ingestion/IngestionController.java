package tw.com.aidenmade.pulseplatform.ingestion;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@Slf4j
public class IngestionController {
    private final EventProducer eventProducer;

    public IngestionController(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@Valid @RequestBody EventRequest request) {
        log.info("event received: eventId={} serviceId={} eventType={} status={} latencyMs={}",
                request.eventId(),
                request.serviceId(),
                request.eventType(),
                request.status(),
                request.latencyMs());
        eventProducer.send(request);
        return ResponseEntity.accepted().build();
    }
}
