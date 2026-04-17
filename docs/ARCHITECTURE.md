# Architecture

> Pulse Platform 的系統架構設計文件。
> 本文件說明**「系統怎麼運作」與「為什麼這樣設計」**,技術選型的決策紀錄請見 [DECISIONS.md](DECISIONS.md)。

---

## 1. 系統概覽

### 1.1 高層架構圖

```
┌─────────┐    ┌──────────────┐    ┌──────────┐    ┌──────────┐
│ Client  │───▶│ Ingestion API│───▶│  Kafka   │───▶│ Consumer │
└─────────┘    └──────────────┘    └──────────┘    └─────┬────┘
                                                         │
                           ┌─────────────────────────────┼─────────┐
                           ▼                             ▼         ▼
                     ┌──────────┐                 ┌──────────┐  ┌──────────┐
                     │PostgreSQL│                 │  Redis   │  │  Alert   │
                     │(events)  │                 │(metrics) │  │ (Phase 2)│
                     └──────────┘                 └─────┬────┘  └──────────┘
                                                        │
                                                        ▼
                                                  ┌──────────┐
                                                  │WebSocket │
                                                  │  Push    │
                                                  └─────┬────┘
                                                        ▼
                                                  ┌──────────┐
                                                  │Dashboard │
                                                  └──────────┘
```

### 1.2 資料流

一筆事件從進入系統到顯示於 Dashboard 的完整流程:

1. **Client** 呼叫 `POST /events`,帶入 `eventId`、`serviceId`、`eventType`、`status`、`latencyMs`
2. **Ingestion API** 驗證格式,以 `serviceId` 為 partition key 寫入 Kafka topic `events.raw`
3. **Consumer** 從 Kafka 拉取,同時做三件事:
   - 寫入 PostgreSQL(原始事件落庫)
   - 更新 Redis 即時指標(當前分鐘 bucket 的 count / error / latency_sum)
   - (Phase 2) 判斷是否觸發告警
4. **Scheduler** 每秒從 Redis 讀取最新 metrics,透過 STOMP 推送到 `/topic/metrics/{serviceId}`
5. **Dashboard** 訂閱 topic,即時更新圖表

**SLA 目標**:事件從進入 Ingestion 到顯示在 Dashboard,延遲 < 2 秒。

---

## 2. 模組設計

### 2.1 Ingestion 模組

**職責**:接收 HTTP 請求,驗證後寫入 Kafka。

```
ingestion/
├── IngestionController.java    ← REST endpoint
├── EventRequest.java           ← 輸入 DTO (record)
├── EventValidator.java         ← 驗證邏輯
└── EventProducer.java          ← Kafka producer wrapper
```

**設計要點**:
- Controller 不做重邏輯,只負責驗證 + 發送
- 使用 `CompletableFuture` 非同步寫 Kafka(Spring Kafka 預設行為)
- Producer config: `acks=all`、`enable.idempotence=true`
- Partition key: `serviceId`(保證同 service 順序)

### 2.2 Consumer 模組

**職責**:消費 Kafka,分派到下游處理器。

```
consumer/
├── EventConsumer.java          ← @KafkaListener 入口
├── EventProcessor.java         ← 協調下游處理
└── ConsumerConfig.java         ← Listener container 設定
```

**設計要點**:
- `enable-auto-commit=false`,手動 commit(為 at-least-once 鋪路)
- Consumer group: `pulse-main-consumer`
- 並行度:`concurrency=4`(對應 8 partitions,每個 consumer 處理 2 個)
- 處理失敗:Phase 1 直接 log 並 commit(簡化);Phase 2 才加 DLQ

### 2.3 Aggregation 模組

**職責**:將事件即時聚合為指標,寫入 Redis。

```
aggregation/
├── MetricsAggregator.java          ← 聚合邏輯
├── RedisMetricsRepository.java     ← Redis 存取
└── MetricsBucket.java              ← 時間窗邏輯
```

**Redis Key 設計(Phase 1 簡化版)**:

```
metrics:{serviceId}:count:{minuteBucket}         → INCR
metrics:{serviceId}:error:{minuteBucket}         → INCR
metrics:{serviceId}:latency_sum:{minuteBucket}   → INCRBY latency
metrics:{serviceId}:latency_count:{minuteBucket} → INCR (用於算平均)
```

- TTL:1 小時(自動清理)
- 時間 bucket 格式:`yyyyMMddHHmm`(UTC)

> ⚠️ Phase 1 只算平均延遲。P95 / P99 需要 sliding window 或 t-digest,Phase 2 再深化。

### 2.4 Persistence 模組

**職責**:將原始事件寫入 PostgreSQL。

```
persistence/
├── EventEntity.java            ← JPA entity
├── EventRepository.java        ← Spring Data JPA
└── EventJdbcClient.java        ← 複雜查詢用 JdbcClient
```

**Schema**:

```sql
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) UNIQUE NOT NULL,
    service_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    latency_ms INT,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_service_created ON events(service_id, created_at DESC);
CREATE INDEX idx_events_status_created ON events(status, created_at DESC) WHERE status = 'ERROR';
```

**`event_id UNIQUE`**:為 Phase 2 冪等預留,資料庫層防重。

### 2.5 Realtime 模組

**職責**:將 metrics 推送給訂閱的前端。

```
realtime/
├── WebSocketConfig.java        ← STOMP broker 設定
├── MetricsPublisher.java       ← @Scheduled 推送
└── SubscriptionManager.java    ← 管理訂閱的 serviceId
```

**Topic 設計**:
- `/topic/metrics/{serviceId}` — 單一 service 的指標
- `/topic/metrics/global` — 全域指標(Phase 2)

---

## 3. Kafka 架構設計

### 3.1 Topic 設計

| Topic | Partition 數 | Retention | 用途 |
|---|---|---|---|
| `monitoring.events.raw` | 8 | 7 天 | 原始事件 |
| `monitoring.events.dlq` | 3 | 14 天 | 死信佇列(Phase 2) |
| `monitoring.alerts` | 3 | 3 天 | 告警事件(Phase 2) |

**為什麼選 8 個 partition?**

- 單一 consumer instance 能處理約 1~2K msg/s
- 預期目標:10K msg/s
- 8 partitions 允許最多 8 個 consumer 並行,預留擴展空間
- 8 是 2 的次方,方便未來用 hash 做 re-partition

### 3.2 Partition Key 策略

**選擇:`serviceId`**

理由:
- ✅ 保證同一 service 的事件有序
- ✅ 粒度適中,熱點風險低(相比用 userId)
- ✅ 聚合時 consumer 可用 local state(若 Phase 2 需要)

風險與緩解:
- ⚠️ 若單一熱門 service 流量極大 → 用 `serviceId:bucket` 複合 key
- ⚠️ Partition skew 監控:Phase 2 加入 Kafka lag 告警

### 3.3 Consumer Group 策略

| Group | 用途 | 並行度 |
|---|---|---|
| `pulse-main-consumer` | 落庫 + 聚合(Phase 1 合一) | 4 |
| `pulse-alerting` | 告警判斷(Phase 2) | 2 |

**為什麼 Phase 1 不分開 persister 和 aggregator?**

簡化 Phase 1,避免過度設計。若壓測發現瓶頸,Phase 2 再拆(會寫進 DECISIONS.md 作為 ADR 更新)。

---

## 4. 可靠性設計

### 4.1 訊息不遺失(Producer 端)

- `acks=all`:所有 in-sync replica 確認
- `retries=Integer.MAX_VALUE`:無限重試(搭配 `delivery.timeout.ms`)
- `enable.idempotence=true`:防止 producer 重試產生重複

### 4.2 訊息不遺失(Consumer 端)

- `enable-auto-commit=false`:處理完再手動 commit
- 處理邏輯在 commit 前完成,確保 at-least-once

### 4.3 冪等性(Phase 2)

- `eventId` 作為業務層防重鍵
- Redis SETNX + TTL 做第一層快速檢查
- PostgreSQL `event_id UNIQUE` constraint 做最終保證

### 4.4 順序保證

- Partition key = `serviceId` → 同一 service 事件進同一 partition → Kafka 保證 partition 內有序
- Consumer 單執行緒處理同一 partition(預設行為)

### 4.5 錯誤處理(Phase 2)

- 可重試錯誤(網路、DB 暫時不可用):指數退避重試 3 次
- 不可重試錯誤(格式錯誤):直接進 DLQ
- DLQ 消費者:每 5 分鐘掃描,通知告警

---

## 5. 可擴展性設計

### 5.1 水平擴展

- **Consumer**:受 partition 數限制,8 partitions 最多 8 instances
- **Ingestion API**:無狀態,可任意擴展
- **Redis**:Phase 1 單機,Phase 3 可升 Cluster
- **PostgreSQL**:Phase 1 單機,高流量時可加 read replica

### 5.2 自動擴容(Phase 3)

- **KEDA**:依 Kafka consumer lag 自動擴容 consumer pod
- **HPA**:依 CPU / request rate 擴容 Ingestion API

---

## 6. 觀測性設計

### 6.1 Metrics(Phase 1 就做)

- Micrometer + Prometheus endpoint(`/actuator/prometheus`)
- 自訂 metrics:
  - `pulse.events.ingested.total{status}` — 入口計數
  - `pulse.events.processed.total{service}` — 消費計數
  - `pulse.processing.duration{phase}` — 各階段延遲

### 6.2 Logging

- Logback + JSON encoder
- 每筆事件帶 `eventId`(traceability)
- 日誌等級:DEBUG(本地)、INFO(正式)

### 6.3 Tracing(Phase 2)

- OpenTelemetry
- Trace 範圍:API → Kafka → Consumer → DB/Redis → WebSocket

---

## 7. 安全性(簡化版)

> Phase 1 不做嚴格安全控制,聚焦架構展示。Phase 2+ 才考慮:

- API Key 認證(`X-API-Key` header)
- Rate limiting(Redis + Token Bucket)
- TLS(K8s 階段才有意義)

---

## 8. 部署架構

### 8.1 Phase 1:Docker Compose

```yaml
services:
  kafka:        # KRaft 模式,單 broker
  redis:        # 單機
  postgres:     # 單機
  app:          # Spring Boot
  grafana:      # 可選
```

### 8.2 Phase 3:K8s

- 每個元件一個 Deployment
- Kafka 用 Strimzi operator
- PostgreSQL 用 CloudNativePG
- Helm chart 管理部署
