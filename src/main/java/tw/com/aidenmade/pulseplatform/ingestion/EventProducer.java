package tw.com.aidenmade.pulseplatform.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventProducer {

    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);
    private static final String TOPIC = "monitoring.events.raw";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(EventRequest event) {
        kafkaTemplate.send(TOPIC, event.serviceId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send event: eventId={} cause={}",
                                event.eventId(), ex.getMessage());
                    } else {
                        log.debug("Event sent: eventId={} partition={} offset={}",
                                event.eventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
