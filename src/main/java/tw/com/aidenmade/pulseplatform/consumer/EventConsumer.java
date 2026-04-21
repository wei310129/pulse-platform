package tw.com.aidenmade.pulseplatform.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tw.com.aidenmade.pulseplatform.aggregation.MetricsAggregator;
import tw.com.aidenmade.pulseplatform.ingestion.EventRequest;
import tw.com.aidenmade.pulseplatform.persistence.EventEntity;
import tw.com.aidenmade.pulseplatform.persistence.EventRepository;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final EventRepository eventRepository;
    private final MetricsAggregator metricsAggregator;

    public EventConsumer(EventRepository eventRepository, MetricsAggregator metricsAggregator) {
        this.eventRepository = eventRepository;
        this.metricsAggregator = metricsAggregator;
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

        try {
            eventRepository.save(entity);
            log.debug("event persisted: eventId={}", event.eventId());
        } catch (DataIntegrityViolationException e) {
            // 重複事件(重啟後 Kafka offset 重播) — ack 後跳過,Phase 2 再做完整冪等消費
            log.warn("duplicate event skipped: eventId={}", event.eventId());
            ack.acknowledge();
            return;
        }

        metricsAggregator.record(event);
        log.debug("metrics aggregated: eventId={}", event.eventId());

        ack.acknowledge();
    }
}
