package tw.com.aidenmade.pulseplatform.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tw.com.aidenmade.pulseplatform.aggregation.MetricsAggregator;
import tw.com.aidenmade.pulseplatform.ingestion.EventRequest;
import tw.com.aidenmade.pulseplatform.persistence.EventEntity;
import tw.com.aidenmade.pulseplatform.persistence.EventRepository;

@Component
@Slf4j
public class EventConsumer {
    private final EventRepository eventRepository;
    private final MetricsAggregator metricsAggregator;

    public EventConsumer(EventRepository eventRepository, MetricsAggregator metricsAggregator) {
        this.eventRepository = eventRepository;
        this.metricsAggregator = metricsAggregator;
    }

    /**
     * 消費流程：
     *   1. 把事件轉成 JPA entity 寫入 PostgreSQL(長期查詢用)
     *   2. 呼叫 MetricsAggregator 寫 Redis(即時聚合用)
     *   3. 手動 ack — offset 在確認寫完後才 commit,避免遺失資料
     *
     * 冪等處理:DB 有 UNIQUE constraint 在 event_id 上。
     * Kafka at-least-once 語意 + consumer 重啟後可能重播舊訊息,
     * 遇到重複 eventId 時捕捉 DataIntegrityViolationException 跳過即可。
     */
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
