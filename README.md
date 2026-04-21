# Pulse Platform

> **Feel the heartbeat of your systems.**
> 一個高吞吐量的即時事件處理與監控平台

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-3.x-black.svg)](https://kafka.apache.org/)

---

## 🎯 專案簡介

Pulse 是一個 **push-based 的事件處理平台**,專注於展示「如何設計一個能承受高併發寫入、並即時轉換為監控指標的後端系統」。

與 Prometheus 等 pull-based 監控系統不同,Pulse 的定位是**事件流處理架構**——客戶端主動推送事件,系統即時聚合並推播到 Dashboard。

### 核心能力

- 🚀 **高併發事件寫入** — 單機壓測達 X,XXX QPS,P95 延遲 < XX ms
- 🔁 **Kafka 解耦** — 8 partitions 設計,支援 consumer 水平擴展
- ⚡ **即時聚合** — Redis 作為 in-memory metrics store,秒級更新
- 📊 **即時 Dashboard** — WebSocket (STOMP) 推播,資料 < 2 秒見於前端
- 🛡️ **可靠性** — at-least-once 消費 + 冪等設計 + DLQ 錯誤處理
- 📈 **可擴展** — Partition-based 水平擴展,預留 KEDA 自動擴容

> ⚠️ 壓測數字為預計目標,Phase 1 完成後會更新實測數據。

---

## 🏗️ 架構概覽

```
Client
  │  POST /events
  ▼
┌─────────────────┐
│  Ingestion API  │  ← 驗證 / 限流 / 寫入 Kafka
└────────┬────────┘
         ▼
┌─────────────────┐
│   Kafka Topic   │  ← buffer + partition(保順序 + 解耦)
│  events.raw     │
└────────┬────────┘
         ▼
┌─────────────────┐
│    Consumer     │
├─────────────────┤
│ ├─ 落庫 → PG    │
│ ├─ 聚合 → Redis │
│ └─ 告警判斷     │
└────────┬────────┘
         ▼
┌─────────────────┐
│ WebSocket Push  │  ← STOMP /topic/metrics
└────────┬────────┘
         ▼
    Dashboard
```

詳細架構設計請見 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 🧩 技術棧

| 類別 | 技術 | 版本 |
|---|---|---|
| 語言 | Java | 21 |
| 框架 | Spring Boot | 3.3.x |
| 訊息佇列 | Apache Kafka (KRaft) | 3.x |
| Kafka Client | Spring Kafka | - |
| 資料庫 | PostgreSQL | 16 |
| 快取 / 聚合 | Redis | 7.x |
| 即時推送 | Spring WebSocket + STOMP | - |
| 壓測 | k6 | - |
| 容器化 | Docker Compose | - |
| 觀測性 | Micrometer + Prometheus | - |

技術選型的完整理由請見 [docs/DECISIONS.md](docs/DECISIONS.md)。

---

## 📂 專案結構

```
pulse-platform/
├── README.md                    ← 本檔案
├── CLAUDE.md                    ← Claude Code 指令檔
├── docker-compose.yml           ← 本地開發環境
├── docs/
│   ├── PROJECT_CONTEXT.md       ← 專案完整背景
│   ├── ARCHITECTURE.md          ← 架構設計文件
│   ├── DECISIONS.md             ← 架構決策紀錄 (ADR)
│   └── PROGRESS.md              ← 開發進度追蹤
├── src/
│   └── main/java/tw/com/aidenmade/pulseplatform/
│       ├── ingestion/           ← 事件接收 API
│       ├── consumer/            ← Kafka 消費者
│       ├── aggregation/         ← Redis 即時聚合
│       ├── persistence/         ← PostgreSQL 持久化
│       ├── realtime/            ← WebSocket 推送
│       └── common/              ← 共用設定與 DTO
└── k6/
    └── load-test.js             ← 壓測腳本
```

---

## 🚀 快速開始

### 需求

- Java 21
- Docker & Docker Compose
- (可選) k6 for load testing

### 啟動

```bash
# 啟動基礎設施 (Kafka, Redis, PostgreSQL)
docker compose up -d

# 啟動應用程式
./mvnw spring-boot:run

# 發送測試事件（eventId 必須是合法 UUID，timestamp 必填）
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "018f1e2a-3b4c-7d5e-8f9a-0b1c2d3e4f50",
    "serviceId": "api-gateway",
    "eventType": "REQUEST",
    "status": "SUCCESS",
    "latencyMs": 45,
    "timestamp": "2026-04-21T08:00:00Z"
  }'

# 確認 Redis 有聚合資料
docker exec pulse-redis redis-cli KEYS "metrics:*"
```

---

## 📅 開發階段

本專案採取漸進式開發,分為三個階段:

| Phase | 範圍 | 狀態 |
|---|---|---|
| **Phase 1** | 核心事件流(Ingestion → Kafka → Consumer → Redis → Dashboard) | 🚧 進行中(Step 1–6 完成,Step 7–9 進行中) |
| **Phase 2** | 可靠性(冪等、DLQ、告警、觀測性) | ⏳ 規劃中 |
| **Phase 3** | 雲原生部署(K8s、KEDA 自動擴容) | ⏳ 選配 |

詳細進度請見 [docs/PROGRESS.md](docs/PROGRESS.md)。

---

## 🎓 這個專案展示了什麼

作為作品集專案,Pulse Platform 展示以下系統設計與工程能力:

- **事件驅動架構(EDA)** 的實戰設計與實作
- **Kafka partition、consumer group** 的設計與 trade-off 分析
- **冪等、順序、可靠性** 在分散式系統中的實作手法
- **即時資料處理** 的架構(Ingestion + Stream Processing + Realtime Push)
- **系統可觀測性** 的落地(metrics、logging、tracing)
- **壓測與容量規劃** 的實踐

完整的設計思考與 trade-off 分析,請見 [docs/](docs/) 目錄下的各項文件。

---

## 📝 License

