# Pulse Platform - Project Context

> **這份文件是給 AI 助手(Claude)讀的專案上下文。**
> 每次開新對話時,會透過 Claude Project 的 Knowledge 功能自動載入。

---

## 👤 開發者背景

- **身份**:Java 工程師,3 年經驗
- **技術棧**:Spring Boot 3.3 + Java 21
- **語言偏好**:繁體中文溝通
- **學習風格**:由淺入深、白話講解、偏好業界實戰而非學術
- **偏好原則**:
  - 不要給 Java 17 之前的舊版寫法
  - 不要盲目附和,要誠實指出問題與 trade-off
  - 程式碼要符合 Spring Boot 3.3 新寫法(JdbcClient、RestClient 等)
  - 偏好漸進式開發,每一步都能 demo

---

## 🎯 專案目標

### 核心定位

**「一個高吞吐量的事件處理平台,展示後端系統設計能力」**

這是作品集專案,**定位重點**:
- 不是要取代 Prometheus / Datadog 等產品
- 不是產品化專案,是**架構練習**
- 展示的是「如何設計能承受高併發、可靠、可擴展的後端系統」

### 與其他監控系統的差異

| 系統 | 模式 | 定位 |
|---|---|---|
| Prometheus | Pull-based | 指標收集 |
| ELK | Log-centric | 日誌分析 |
| **Pulse** | **Push-based** | **事件流處理 + 即時指標** |

### 求職目標

這個專案是為了**求職中高階後端工程師職位**的作品集,重點展示:
- 事件驅動架構(EDA)設計能力
- 分散式系統的可靠性設計(冪等、順序、at-least-once)
- 高併發處理經驗
- 系統可觀測性設計

---

## 🏗️ 系統架構

```
Client
  │  POST /events (JSON)
  ▼
[Ingestion API]  ← 驗證、限流、寫 Kafka
  │
  ▼
[Kafka Topic: events.raw]  ← 8 partitions
  │
  ▼
[Consumer Group]
  ├─ 落庫 → PostgreSQL
  ├─ 聚合 → Redis (in-memory metrics)
  └─ 告警判斷 (Phase 2)
        │
        ▼
  [WebSocket (STOMP) Push]
        │
        ▼
   [Dashboard]
```

### 關鍵設計原則

1. **Kafka 為核心 buffer**:解耦 ingestion 和 processing,避免 DB 被打爆
2. **Partition key 用 serviceId**:保證同一 service 的事件順序
3. **at-least-once 消費**:搭配冪等設計確保不重複計算
4. **Redis 做即時聚合**:承受高頻寫入,秒級更新
5. **PostgreSQL 做長期儲存**:事件原始資料、歷史查詢

---

## 🧩 技術選型

### 已確定

| 類別 | 技術 | 版本 | 決策理由 |
|---|---|---|---|
| 語言 | Java | **21** | Virtual Threads、Pattern Matching |
| 框架 | Spring Boot | **3.3.x** | 業界主流,生態成熟 |
| 建置工具 | Gradle | - | Kotlin DSL,比 Maven 靈活 |
| 訊息佇列 | Apache Kafka | **3.x KRaft** | 業界標準,履歷辨識度高 |
| Kafka Client | Spring Kafka | - | 錯誤處理、DLQ、重試都方便 |
| 資料庫 | PostgreSQL | **16** | JSONB、time-series 能力強 |
| 快取/聚合 | Redis | **7.x** 單機 | 原生資料結構夠用 |
| ORM | Spring Data JPA + **JdbcClient** | - | 複雜查詢用 JdbcClient |
| WebSocket | Spring WebSocket + STOMP | - | topic 訂閱機制 |
| 前端 | **Grafana** (Phase 1 偷懶方案) | - | 省時間專注後端 |
| 壓測 | k6 | - | 輕量、報告漂亮 |
| 容器 | Docker Compose | - | Phase 3 才上 K8s |
| 觀測性 | Micrometer + Prometheus | - | Spring Boot 內建 |
| 日誌 | Logback + JSON encoder | - | 結構化日誌 |

### Java Package 命名

```
tw.com.aidenmade.pulseplatform
```

### 不會做的事(避免 scope creep)

- ❌ Phase 1 不做冪等(Phase 2 才做)
- ❌ 不上 Kafka Streams / Flink(用 Consumer 就夠了)
- ❌ 不用 Redpanda / Pulsar(用主流 Kafka)
- ❌ 不自己寫 Dashboard(Phase 1 用 Grafana)
- ❌ Phase 1 不上 K8s(用 Docker Compose)

---

## 📅 階段規劃

### Phase 1:核心事件流(4~6 週)
**目標**:能 demo、能講故事、履歷可寫

- Ingestion API(`POST /events`)
- Kafka Producer + Consumer
- Redis 即時聚合(TPS / error rate / latency)
- PostgreSQL 事件落庫
- WebSocket 推送(或 Grafana)
- k6 壓測 + 報告

### Phase 2:可靠性深化(3~4 週)
**目標**:履歷加深度,面試話題變多

- 冪等消費(eventId 防重)
- DLQ + 指數退避重試
- 告警引擎(錯誤率 / 流量暴增)
- Observability(Prometheus + OpenTelemetry trace)
- 故障測試(consumer crash、DB 慢)

### Phase 3:雲原生(4~8 週,選配)
**目標**:展示 DevOps 能力

- K8s 部署
- KEDA 依 Kafka lag 自動擴容
- Helm chart
- CI/CD pipeline

---

## 🔑 關鍵設計決策(精選)

詳見 [DECISIONS.md](DECISIONS.md),這裡列最重要的:

1. **Partition Key = `serviceId`**:粒度適中,避免單 user 熱點
2. **eventId 從 Day 1 就帶**:為 Phase 2 冪等預留
3. **時間戳統一 UTC**:用 `Instant` / `OffsetDateTime`,禁用 `LocalDateTime`
4. **Consumer 手動 commit**:`enable-auto-commit=false`,at-least-once 基礎
5. **Topic 命名慣例**:`{domain}.{entity}.{event}`,例如 `monitoring.events.raw`
6. **Consumer Group 命名**:依用途分開(`pulse-persister`、`pulse-aggregator`)

---

## 🤖 與 Claude 協作的期望

### 桌面版 Claude(本 Project)負責

- 架構討論、技術選型
- 深度概念講解、trade-off 分析
- 產出 / 更新 docs/ 下的 markdown
- 面試題目演練

### Claude Code 負責

- 實際寫 code、改 code
- 跑指令、debug、測試
- 根據 DECISIONS.md 實作功能

### 對話原則

1. **先評估可行性,不要盲目附和**
2. **指出沒想到的坑**
3. **給 trade-off,不只給答案**
4. **用 Spring Boot 3.3 / Java 21 新寫法**
5. **每次對話結束,協助更新 PROGRESS.md**
6. **範圍太大時,幫忙拆解步驟**

---

## 📝 文件同步機制

所有決策與進度透過 docs/ 下的 markdown 同步:

- `PROJECT_CONTEXT.md`(本檔案)— 長期不變的專案背景
- `ARCHITECTURE.md` — 系統架構設計
- `DECISIONS.md` — 每次架構決策都寫 ADR
- `PROGRESS.md` — 每週更新進度

**流程**:在桌面版討論 → 產出 markdown → commit 到 GitHub → 定期同步到 Claude Project Knowledge。
