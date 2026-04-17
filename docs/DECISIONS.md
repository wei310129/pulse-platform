# Architecture Decision Records (ADR)

> 這份文件記錄所有重要的架構與技術選型決策。
> 每個決策都有編號、日期、狀態、理由與 trade-off。
> **舊的 ADR 不會被刪除**,只會被標記為 `Superseded by ADR-XXX`。

---

## 索引

| 編號 | 標題 | 狀態 | 日期 |
|---|---|---|---|
| ADR-001 | 選擇 Kafka 而非 Redpanda / Pulsar | Accepted | 2026-04-17 |
| ADR-002 | 使用 Spring Kafka 而非原生 Kafka Client | Accepted | 2026-04-17 |
| ADR-003 | 使用 PostgreSQL 而非 MySQL | Accepted | 2026-04-17 |
| ADR-004 | Partition Key 選用 serviceId | Accepted | 2026-04-17 |
| ADR-005 | Consumer 採手動 commit(at-least-once) | Accepted | 2026-04-17 |
| ADR-006 | eventId 從 Day 1 就帶(為冪等預留) | Accepted | 2026-04-17 |
| ADR-007 | 時間戳統一 UTC,禁用 LocalDateTime | Accepted | 2026-04-17 |
| ADR-008 | Phase 1 Dashboard 使用 Grafana 而非自寫前端 | Accepted | 2026-04-17 |
| ADR-009 | Java Package 命名:tw.com.aidenmade.pulseplatform | Accepted | 2026-04-17 |
| ADR-010 | Phase 1 不做冪等,Phase 2 才加 | Accepted | 2026-04-17 |

---

## ADR-001:選擇 Kafka 而非 Redpanda / Pulsar

- **日期**:2026-04-17
- **狀態**:Accepted

### 背景

需要一個高吞吐量的訊息佇列作為事件 buffer。

### 決策

使用 **Apache Kafka 3.x(KRaft 模式,無 Zookeeper)**。

### 理由

- 業界標準,履歷辨識度最高
- KRaft 模式已 GA,省去 Zookeeper 維運
- 生態完整(Spring Kafka、KEDA、Strimzi 都支援)

### 考慮過的替代方案

- **Redpanda**:單一 binary、更輕量,但業界採用率低,作品集價值較低
- **Apache Pulsar**:更先進的架構,但學習曲線陡,偏離「展示主流技術」的目標

### Trade-off

- 比 Redpanda 資源消耗大(需要更多記憶體)
- 對作品集來說完全可接受

---

## ADR-002:使用 Spring Kafka 而非原生 Kafka Client

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

使用 **Spring Kafka (`spring-kafka`)**,不用原生 `kafka-clients`。

### 理由

- `@KafkaListener` 簡化 consumer 程式碼
- Error handler、retry、DLQ 的抽象好
- 與 Spring Boot 3.3 整合順暢
- Phase 2 做 DLQ 時最省力

### Trade-off

- 多一層抽象,debug Kafka 底層行為時需要知道背後實作
- 版本升級時要注意 Spring Kafka 與 kafka-clients 版本對應

---

## ADR-003:使用 PostgreSQL 而非 MySQL

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

使用 **PostgreSQL 16**。

### 理由

- JSONB 支援適合存事件 payload
- Window function 強,聚合查詢好寫
- 未來可升級 TimescaleDB 處理時序資料
- Spring Data JPA + PostgreSQL 生態成熟

### Trade-off

- MySQL 市占率較高,但 PG 在技術型專案更受青睞
- 團隊若只熟 MySQL 需要額外學習成本(對本專案不成立)

---

## ADR-004:Partition Key 選用 serviceId

- **日期**:2026-04-17
- **狀態**:Accepted

### 背景

需要決定 Kafka 的 partition key,同時考慮**順序保證**與**負載均衡**。

### 決策

使用 `serviceId` 作為 partition key。

### 理由

- 粒度適中,大多數應用的 service 數量在數十到數百之間
- 保證同一 service 的事件有序
- 未來若做 local state aggregation,同 service 資料會在同一 consumer

### 考慮過的替代方案

| 選項 | 優點 | 缺點 |
|---|---|---|
| `userId` | 最細粒度 | 單一熱門 user 會造成 partition skew |
| `serviceId` ✅ | 均衡 | 若單 service 極熱仍可能不均 |
| 隨機 | 完美均衡 | 喪失順序保證 |
| `eventType` | 語意清楚 | 粒度太粗,容易熱點 |

### Trade-off

- 若未來有單一 service 流量暴增(> 10K events/sec),需要改用複合 key `serviceId:bucket`
- 此時會開新 ADR 取代本條

---

## ADR-005:Consumer 採手動 commit(at-least-once)

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

`enable-auto-commit=false`,處理完業務邏輯後才手動 commit offset。

### 理由

- 確保至少處理一次(at-least-once)
- 為 Phase 2 的冪等設計鋪路
- 符合業界對關鍵資料的可靠性要求

### Trade-off

- 可能造成重複消費(消費者在 commit 前 crash)
- **必須搭配冪等設計**(Phase 2 實作)
- Phase 1 暫時接受「可能重複計算指標」的風險(聚合容錯能力高)

---

## ADR-006:eventId 從 Day 1 就帶(為冪等預留)

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

即使 Phase 1 不做冪等,`POST /events` 也強制要求 `eventId` 欄位。

### 理由

- 客戶端若未產生 eventId,之後補加會改動 API schema(breaking change)
- PostgreSQL 的 `event_id UNIQUE` constraint 從 Day 1 就建立
- Phase 2 做冪等時,資料層已經準備好

### 實作細節

- 格式:UUID v7(時間有序,便於分區索引)
- 驗證:Ingestion API 檢查格式 + 長度
- 缺失時:回傳 400

---

## ADR-007:時間戳統一 UTC,禁用 LocalDateTime

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

所有時間戳使用 `Instant` 或 `OffsetDateTime`,一律存為 UTC。

### 理由

- `LocalDateTime` 沒有時區資訊,跨時區部署會出問題
- Kafka 訊息、資料庫、Redis key 都統一 UTC,避免混亂
- 前端顯示時才做時區轉換

### 實作細節

- PostgreSQL 使用 `TIMESTAMPTZ`
- Jackson 設定 `WRITE_DATES_AS_TIMESTAMPS=false`,輸出 ISO-8601
- Redis key 的時間 bucket 也用 UTC(如 `202604171430`)

### Trade-off

- 前端需要做時區轉換(這是正確的做法)
- 本地 debug 時要習慣看 UTC 時間

---

## ADR-008:Phase 1 Dashboard 使用 Grafana 而非自寫前端

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

Phase 1 直接使用 Grafana,接 PostgreSQL 和 Redis 畫圖。

### 理由

- 作品集的重點是**後端架構**,不是前端
- 自寫 React dashboard 會吃掉 2~3 週時間
- Grafana 接 PG 用 SQL 畫圖即可,1 天搞定

### Trade-off

- 失去「WebSocket 即時推送」的展示點
- 解法:Phase 2 再補一個簡單的 React demo page 驗證 WebSocket

### 後續行動

- Phase 2:補一頁 React + STOMP 的 demo,證明即時推送可行

---

## ADR-009:Java Package 命名:tw.com.aidenmade.pulseplatform

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

Java base package:`tw.com.aidenmade.pulseplatform`

### 理由

- 遵循「反向 domain」命名慣例(業界標準)
- `tw.com.aidenmade` 是開發者個人品牌
- `pulseplatform` 作為專案區隔

### Sub-package 結構

```
tw.com.aidenmade.pulseplatform
├── ingestion       ← 事件接收
├── consumer        ← Kafka 消費
├── aggregation     ← Redis 聚合
├── persistence     ← PG 持久化
├── realtime        ← WebSocket 推送
└── common          ← 共用設定、DTO
```

---

## ADR-010:Phase 1 不做冪等,Phase 2 才加

- **日期**:2026-04-17
- **狀態**:Accepted

### 決策

Phase 1 的 Consumer 不檢查 `eventId` 是否重複,允許重複計算。

### 理由

- Phase 1 目標是「資料流打通」,盡快 demo
- 加冪等會需要 Redis SETNX + TTL + DB unique,複雜度上升
- 聚合指標對少量重複容錯度高(平均值誤差 < 1%)

### 前置準備(Phase 1 就做)

- `eventId` 欄位從 Day 1 帶入(ADR-006)
- PG table 的 `event_id UNIQUE` constraint 從 Day 1 建立
- 時間戳與 partition key 設計就緒

### Phase 2 實作方向

- 第一層:Redis SETNX(`idempotency:{eventId}`, TTL 1 小時)
- 第二層:PG INSERT ... ON CONFLICT DO NOTHING
- 監控:重複事件比率 metric

---

## ADR 撰寫模板(新增決策時複製此段)

```markdown
## ADR-XXX:[決策標題]

- **日期**:YYYY-MM-DD
- **狀態**:Proposed / Accepted / Superseded by ADR-YYY

### 背景
(為什麼要做這個決策)

### 決策
(決定了什麼)

### 理由
(為什麼這樣決定)

### 考慮過的替代方案
(其他選項與比較)

### Trade-off
(這個決策的代價)
```
