# Development Progress

> 本專案開發進度追蹤。每週至少更新一次。

**最後更新**:2026-04-17
**目前階段**:Phase 1 進行中
**目前 Step**:Step 7 — WebSocket 推送

---

## 📊 總覽

| Phase | 範圍 | 預估時程 | 狀態 | 實際完成日 |
|---|---|---|---|---|
| Phase 1 | 核心事件流 | 4~6 週 | 🚧 進行中 | - |
| Phase 2 | 可靠性深化 | 3~4 週 | ⏳ 未開始 | - |
| Phase 3 | K8s + KEDA | 4~8 週 | ⏳ 未開始 | - |

---

## Phase 1 — 核心事件流

**目標**:打通 Client → Kafka → Consumer → Redis/PG → Dashboard 的完整資料流,能做壓測並產出數據。

### Step 1:基礎設施(Docker Compose)

- [x] 建立 `docker-compose.yml`
- [x] Kafka(KRaft 模式,單 broker)
- [x] Redis 7.x 單機
- [x] PostgreSQL 16
- [x] 三者 healthcheck 全綠
- [x] 手動測試:`kafka-console-producer` / `redis-cli` / `psql` 都能連

**完成判定**:`docker compose up -d` 三個服務 healthy。
**預估工時**:半天

### Step 2:最小 Ingestion API

- [x] 建立 Spring Boot 3.3 專案(Maven)
- [x] Base package:`tw.com.aidenmade.pulseplatform`
- [x] `POST /events` endpoint(先只 log)
- [x] `EventRequest` record(含 `eventId`、`serviceId`、`eventType`、`status`、`latencyMs`)
- [x] 驗證:eventId 必填、格式檢查
- [x] 回傳 202 Accepted

**完成判定**:curl 打 API 能看到 log。
**預估工時**:半天 ~ 1 天

### Step 3:接上 Kafka Producer

- [x] 加入 `spring-kafka` 依賴
- [x] `KafkaProducerConfig`:`acks=all`、`enable.idempotence=true`
- [x] Topic 命名:`monitoring.events.raw`(8 partitions)
- [x] `EventProducer` 發送訊息,key = `serviceId`
- [x] JSON 序列化(Jackson)

**完成判定**:`kafka-console-consumer` 從 topic 讀得到訊息。
**預估工時**:1 天

### Step 4:Kafka Consumer

- [x] `@KafkaListener` 消費 `monitoring.events.raw`
- [x] Consumer group:`pulse-main-consumer`
- [x] `enable-auto-commit=false`,手動 commit
- [x] 並行度 `concurrency=4`
- [x] 先只印 log,驗證消費行為

**完成判定**:Producer 發,Consumer log 看得到。
**預估工時**:1 天

### Step 5:事件落 PostgreSQL

- [x] 建立 `events` table(含 `event_id UNIQUE`)
- [x] Flyway migration 管理 schema
- [x] `EventEntity`(JPA)
- [x] `EventRepository`(Spring Data JPA)
- [x] Consumer 處理時寫入 DB

**完成判定**:打 API → `SELECT * FROM events` 有資料。
**預估工時**:1 天

### Step 6:Redis 即時聚合

- [x] 加入 `spring-boot-starter-data-redis`
- [x] `MetricsAggregator`:根據事件更新 Redis keys
- [x] Key 設計:`metrics:{serviceId}:count:{bucket}` 等
- [x] TTL 設 1 小時
- [x] 單元測試:事件進來,Redis 數字正確

**完成判定**:壓測 1000 筆,`redis-cli` 看數字正確。
**預估工時**:2 天

### Step 7:WebSocket 推送

- [ ] 加入 `spring-boot-starter-websocket`
- [ ] `WebSocketConfig`:STOMP endpoint 與 broker
- [ ] `MetricsPublisher`:`@Scheduled(fixedRate=1000)` 推送
- [ ] Topic:`/topic/metrics/{serviceId}`

**完成判定**:用 `websocat` 或瀏覽器 console 連線,每秒收到資料。
**預估工時**:2 天

### Step 8:Dashboard(Grafana)

- [ ] `docker-compose.yml` 加入 Grafana
- [ ] 設定 PostgreSQL datasource
- [ ] 建立 dashboard:TPS / Error Rate / Avg Latency
- [ ] 截圖放入 README

**完成判定**:打開 Grafana 看到即時圖表。
**預估工時**:1 天

### Step 9:壓測與報告

- [ ] 安裝 k6
- [ ] 撰寫 `load-test.js`(階梯加壓)
- [ ] 執行壓測,記錄 QPS / P50 / P95 / Error Rate
- [ ] 觀察 Kafka consumer lag、Redis CPU、PG 負載
- [ ] 撰寫壓測報告(`docs/LOAD_TEST_REPORT.md`)
- [ ] 更新 README 的壓測數據

**完成判定**:README 有實測數據,壓測報告完整。
**預估工時**:2~3 天

### Phase 1 完成條件

- [ ] Step 1~9 全部完成
- [ ] README 有完整介紹與實測數據
- [ ] 履歷可以寫:「**Pulse Platform** - 高吞吐量事件處理平台(單機 XXX QPS)」
- [ ] 可以 demo 給面試官看

---

## Phase 2 — 可靠性深化(規劃中,細節暫定)

- [ ] 冪等消費(Redis SETNX + PG UNIQUE)
- [ ] DLQ + 指數退避重試
- [ ] 告警引擎(錯誤率、流量暴增)
- [ ] Observability(Prometheus metrics、OpenTelemetry trace)
- [ ] 故障測試(consumer crash、DB 慢、Kafka broker 重啟)
- [ ] P95/P99 延遲計算(sliding window 或 t-digest)

---

## Phase 3 — 雲原生部署(選配)

- [ ] Dockerfile 最佳化(multi-stage build)
- [ ] K8s manifests(Deployment、Service、ConfigMap)
- [ ] Helm chart
- [ ] KEDA:依 Kafka consumer lag 自動擴容
- [ ] CI/CD pipeline(GitHub Actions)

---

## 📝 週報(新增時放最上面)

### Week 0 - 2026-04-17

**本週完成**:
- 確立專案定位與三階段規劃
- 完成技術選型(ADR-001 ~ ADR-010)
- 建立 GitHub repo: `pulse-platform`
- 產出完整文件:README、PROJECT_CONTEXT、ARCHITECTURE、DECISIONS、PROGRESS

**下週目標**:
- Step 1:完成 Docker Compose 基礎設施
- Step 2:最小 Ingestion API

**卡關 / 疑問**:
- (無)

---

## 🎯 里程碑

| 里程碑 | 目標日期 | 實際完成 | 備註 |
|---|---|---|---|
| M1:Docker Compose + API 骨架(Step 1-2) | +1 週 | - | - |
| M2:Kafka 端到端打通(Step 3-4) | +2 週 | - | - |
| M3:資料完整落地(Step 5-6) | +3 週 | - | - |
| M4:Dashboard 可展示(Step 7-8) | +4 週 | - | - |
| M5:Phase 1 完成(Step 9 + 壓測報告) | +6 週 | - | - |

---

## 📦 學習紀錄

紀錄開發過程中學到的新東西,未來可寫成技術文章。

- (待填入)
