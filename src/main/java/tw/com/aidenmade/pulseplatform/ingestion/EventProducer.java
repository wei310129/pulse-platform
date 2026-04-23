package tw.com.aidenmade.pulseplatform.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EventProducer {
    private static final String TOPIC = "monitoring.events.raw";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 將事件送入 Kafka。
     * key = serviceId:同一個 service 的事件會落在同一個 partition,保證消費順序(ADR-004)。
     * 發送結果用 whenComplete 非同步處理,不阻塞 HTTP 執行緒。
     */
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
