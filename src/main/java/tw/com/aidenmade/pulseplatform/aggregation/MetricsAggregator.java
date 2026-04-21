package tw.com.aidenmade.pulseplatform.aggregation;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tw.com.aidenmade.pulseplatform.ingestion.EventRequest;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 消費事件後,將即時指標寫入 Redis。
 *
 * Key 結構(見 CLAUDE.md 命名規範):
 *   metrics:{serviceId}:count:{minuteBucket}       — 總請求數
 *   metrics:{serviceId}:error:{minuteBucket}       — 錯誤數(status=ERROR 時才寫)
 *   metrics:{serviceId}:latency_sum:{minuteBucket} — 延遲毫秒累加(latencyMs 不為 null 時才寫)
 *
 * bucket 格式:yyyyMMddHHmm(UTC),例如 202604211435
 * TTL:1 小時
 */
@Service
public class MetricsAggregator {

    static final long TTL_SECONDS = 3600;

    private static final DateTimeFormatter BUCKET_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final StringRedisTemplate redis;

    public MetricsAggregator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void record(EventRequest event) {
        String bucket = BUCKET_FMT.format(event.timestamp());
        String prefix = "metrics:" + event.serviceId();
        Duration ttl = Duration.ofSeconds(TTL_SECONDS);

        // 每筆事件都累加 count
        incrementAndExpire(prefix + ":count:" + bucket, 1L, ttl);

        // 只有 ERROR 才累加 error
        if ("ERROR".equals(event.status())) {
            incrementAndExpire(prefix + ":error:" + bucket, 1L, ttl);
        }

        // latencyMs 可能為 null(呼叫端未帶),跳過
        if (event.latencyMs() != null) {
            incrementAndExpire(prefix + ":latency_sum:" + bucket, event.latencyMs(), ttl);
        }
    }

    private void incrementAndExpire(String key, long delta, Duration ttl) {
        redis.opsForValue().increment(key, delta);
        redis.expire(key, ttl);
    }
}
