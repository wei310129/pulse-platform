package tw.com.aidenmade.pulseplatform.ingestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;

/**
 * 進入系統的原始事件。
 * eventId 格式為 UUID v7(時間有序),由呼叫端產生 — 見 ADR-006。
 * timestamp 必須是 UTC — 見 ADR-007。
 */
public record EventRequest(

        @NotBlank(message = "eventId is required")
        @Pattern(
                regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                message = "eventId must be a valid UUID"
        )
        String eventId,

        @NotBlank(message = "serviceId is required")
        String serviceId,

        @NotBlank(message = "eventType is required")
        String eventType,

        @NotBlank(message = "status is required")
        String status,

        Integer latencyMs,

        @NotNull(message = "timestamp is required")
        Instant timestamp,

        Map<String, Object> metadata

) {}
