package tw.com.aidenmade.pulseplatform.ingestion;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);

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
