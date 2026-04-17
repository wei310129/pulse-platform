package tw.com.aidenmade.pulseplatform.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tw.com.aidenmade.pulseplatform.ingestion.EventRequest;
import tw.com.aidenmade.pulseplatform.persistence.EventEntity;
import tw.com.aidenmade.pulseplatform.persistence.EventRepository;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final EventRepository eventRepository;

    public EventConsumer(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @KafkaListener(topics = "monitoring.events.raw")
    public void consume(EventRequest event, Acknowledgment ack) {
        log.info("event consumed: eventId={} serviceId={} eventType={} status={} latencyMs={}",
                event.eventId(),
                event.serviceId(),
                event.eventType(),
                event.status(),
                event.latencyMs());

        var entity = EventEntity.builder()
                .eventId(event.eventId())
                .serviceId(event.serviceId())
                .eventType(event.eventType())
                .status(event.status())
                .latencyMs(event.latencyMs())
                .payload(event.metadata())
                .createdAt(event.timestamp())
                .build();

        eventRepository.save(entity);
        log.debug("event persisted: eventId={}", event.eventId());

        ack.acknowledge();
    }
}
