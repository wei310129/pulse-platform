package tw.com.aidenmade.pulseplatform.aggregation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tw.com.aidenmade.pulseplatform.ingestion.EventRequest;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsAggregatorTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @InjectMocks
    MetricsAggregator aggregator;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ── bucket helper ──────────────────────────────────────────────────────
    // 2026-04-21T14:35:00Z → bucket = "202604211435"
    private static final Instant T = Instant.parse("2026-04-21T14:35:00Z");
    private static final String SVC = "order-service";
    private static final Duration TTL = Duration.ofSeconds(MetricsAggregator.TTL_SECONDS);

    @Test
    void success_event_increments_count_and_latency_only() {
        var event = new EventRequest("evt-001", SVC, "REQUEST", "SUCCESS", 42, T, null);

        aggregator.record(event);

        verify(valueOps).increment("metrics:order-service:count:202604211435", 1L);
        verify(valueOps).increment("metrics:order-service:latency_sum:202604211435", 42L);
        verify(valueOps, never()).increment(contains(":error:"), anyLong());

        // count + latency_sum = 2 expire calls
        verify(redis, times(2)).expire(anyString(), eq(TTL));
    }

    @Test
    void error_event_also_increments_error_key() {
        var event = new EventRequest("evt-002", SVC, "REQUEST", "ERROR", 150, T, null);

        aggregator.record(event);

        verify(valueOps).increment("metrics:order-service:count:202604211435", 1L);
        verify(valueOps).increment("metrics:order-service:error:202604211435", 1L);
        verify(valueOps).increment("metrics:order-service:latency_sum:202604211435", 150L);

        // count + error + latency_sum = 3 expire calls
        verify(redis, times(3)).expire(anyString(), eq(TTL));
    }

    @Test
    void null_latency_skips_latency_sum_key() {
        var event = new EventRequest("evt-003", SVC, "ERROR", "ERROR", null, T, null);

        aggregator.record(event);

        verify(valueOps).increment("metrics:order-service:count:202604211435", 1L);
        verify(valueOps).increment("metrics:order-service:error:202604211435", 1L);
        verify(valueOps, never()).increment(contains(":latency_sum:"), anyLong());

        verify(redis, times(2)).expire(anyString(), eq(TTL));
    }

    @Test
    void keys_include_correct_minute_bucket() {
        // 14:35:59 和 14:36:00 應落在不同 bucket
        var at3559 = new EventRequest("evt-004", SVC, "REQUEST", "SUCCESS", 10,
                Instant.parse("2026-04-21T14:35:59Z"), null);
        var at3600 = new EventRequest("evt-005", SVC, "REQUEST", "SUCCESS", 10,
                Instant.parse("2026-04-21T14:36:00Z"), null);

        aggregator.record(at3559);
        aggregator.record(at3600);

        verify(valueOps).increment("metrics:order-service:count:202604211435", 1L);
        verify(valueOps).increment("metrics:order-service:count:202604211436", 1L);
    }
}
