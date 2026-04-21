package tw.com.aidenmade.pulseplatform.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * `events` 資料表的 JPA mapping。
 *
 * Lombok 說明:
 *   @Getter          — 只產生 getter,沒有 setter(immutable 設計)
 *   @Builder         — 用 builder pattern 建立物件,不直接用 constructor
 *   @NoArgsConstructor(PROTECTED) — JPA 需要無參 constructor,但設為 protected 防止外部直接 new
 *   @AllArgsConstructor(PRIVATE)  — 配合 @Builder 使用,設為 private 讓外部只能用 builder
 *
 * event_id 有 UNIQUE constraint,用來保證冪等(防 Kafka 重播寫入重複資料)。
 * payload 欄位型別為 jsonb,儲存 EventRequest.metadata 的彈性擴充欄位。
 */
@Entity
@Table(name = "events")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "service_id", nullable = false, length = 64)
    private String serviceId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
