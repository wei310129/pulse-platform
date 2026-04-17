# Claude Code Instructions

> 本檔案是 Claude Code 進入此專案時自動讀取的指令檔。
> 內容包含專案上下文、編碼規範、當前進度,確保 Claude Code 產出一致的程式碼。

---

## 🎯 Project Context

請**優先**讀取以下文件以了解專案全貌:

1. [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) — 專案完整背景
2. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — 系統架構
3. [`docs/DECISIONS.md`](docs/DECISIONS.md) — 所有技術決策(ADR)
4. [`docs/PROGRESS.md`](docs/PROGRESS.md) — 當前進度與 TODO

**重要原則**:任何新程式碼必須符合 DECISIONS.md 中已記錄的決策。若與 ADR 衝突,應先討論並更新 ADR,再動手寫 code。

---

## 👤 開發者資訊

- **身份**:Java 工程師,3 年經驗
- **語言**:繁體中文
- **偏好**:由淺入深講解、重視 trade-off 分析
- **Java package**:`tw.com.aidenmade.pulseplatform`

---

## 🧑‍💻 Coding Standards

### 語言與框架版本

- **Java 21**(**不要**用 Java 17 之前的寫法)
- **Spring Boot 3.3.x**(**不要**用 deprecated API)
- **Maven**(見 ADR-011)

### Java 21 特性優先使用

- ✅ **Records** 取代 POJO(特別是 DTO、VO)
- ✅ **Pattern Matching**(`instanceof` 簡化)
- ✅ **Switch Expression**(取代舊版 switch)
- ✅ **Virtual Threads**(I/O 密集場景)
- ✅ **Sealed Classes**(表達 closed hierarchy)
- ✅ **Text Blocks**(多行字串)

### Spring Boot 3.3 慣例

- ✅ 用 `JdbcClient` 而非 `JdbcTemplate`(Spring 6.1+)
- ✅ 用 `RestClient` 而非 `RestTemplate` / `WebClient`(同步場景)
- ✅ `@Configuration(proxyBeanMethods = false)` 作為預設
- ✅ Jakarta EE(`jakarta.*`)而非 `javax.*`
- ❌ **不要用** `WebSecurityConfigurerAdapter`(已移除)
- ❌ **不要用** `@EnableAutoConfiguration`(預設已開)

### 程式碼風格

- **不要濫用 Lombok**:DTO 一律用 `record`,只有複雜 entity 才用 Lombok
- **避免 setter**:優先用 Builder 或 Record
- **Immutable 優先**:`List.of()`、`Map.of()`、Records
- **早期返回**:減少巢狀 if
- **包裝例外**:不要裸拋 `RuntimeException`,用具名的 custom exception

### 命名規範

| 類型 | 規範 | 範例 |
|---|---|---|
| 類別 | PascalCase | `EventProducer` |
| 方法 | camelCase | `processEvent` |
| 常數 | UPPER_SNAKE | `MAX_BATCH_SIZE` |
| Package | 全小寫,單字 | `ingestion`、`consumer` |
| Kafka topic | kebab-case + dot | `monitoring.events.raw` |
| Consumer group | kebab-case | `pulse-main-consumer` |
| Redis key | colon 分隔 | `metrics:svc1:count:202604171430` |

### 時間處理(重要)

- ✅ **只用** `Instant`(時間點)或 `OffsetDateTime`(帶時區)
- ❌ **禁用** `LocalDateTime`(無時區資訊,跨時區會出問題)
- ✅ DB 欄位用 `TIMESTAMPTZ`
- ✅ JSON 序列化用 ISO-8601

### 測試規範

- JUnit 5(`org.junit.jupiter`)
- AssertJ 斷言(不用 Hamcrest)
- Testcontainers 測 Kafka / Redis / PostgreSQL
- 測試類別:`XxxTest`(單元)、`XxxIT`(整合)
- 不追求 100% coverage,追求**關鍵路徑 + edge case**

---

## 📁 Package 結構

```
tw.com.aidenmade.pulseplatform
├── ingestion/       ← REST API 收事件
├── consumer/        ← Kafka 消費
├── aggregation/     ← Redis 聚合
├── persistence/     ← PostgreSQL 持久化
├── realtime/        ← WebSocket / STOMP 推送
└── common/          ← 共用 DTO、config、exception
    ├── config/
    ├── dto/
    └── exception/
```

**模組邊界原則**:
- 模組之間透過介面溝通,不直接 import 對方的 impl
- `common` 可被任何模組依賴,但不能依賴其他模組

---

## 🧩 關鍵業務規則

### Event DTO 規範

所有事件都必須帶以下欄位:

```java
public record EventRequest(
    String eventId,      // UUID v7,必填(ADR-006)
    String serviceId,    // partition key(ADR-004)
    String eventType,    // REQUEST / ERROR / CUSTOM
    String status,       // SUCCESS / ERROR
    Integer latencyMs,   // 延遲毫秒數
    Instant timestamp,   // 事件發生時間(UTC)
    Map<String, Object> metadata  // 可選擴充欄位
) {}
```

### Kafka Topic 命名規則

格式:`{domain}.{entity}.{event}`

- `monitoring.events.raw` — 原始事件
- `monitoring.events.dlq` — 死信(Phase 2)
- `monitoring.alerts` — 告警(Phase 2)

### Redis Key 命名規則

格式:`{scope}:{identifier}:{attribute}:{bucket?}`

- `metrics:{serviceId}:count:{minuteBucket}`
- `metrics:{serviceId}:error:{minuteBucket}`
- `idempotency:{eventId}`(Phase 2)

---

## 🚦 當前開發階段

**Phase 1,Step 1 準備中**

詳見 [`docs/PROGRESS.md`](docs/PROGRESS.md) 的 checklist。

執行任務時,請:
1. 先確認任務屬於哪個 Step
2. 檢查該 Step 的 checklist
3. 完成後協助勾選對應項目

---

## 🤖 與開發者協作的原則

1. **先讀 docs,再動手**:寫任何 code 前先確認 ADR 與 PROGRESS
2. **誠實指出問題**:不要盲目附和,發現設計有問題要直接說
3. **trade-off 思維**:給方案時同時說明代價
4. **漸進式**:一次做一小步,能跑再往下
5. **記錄決策**:有新的架構決定,協助產出 ADR 更新 DECISIONS.md
6. **更新進度**:完成一個 Step 後,協助更新 PROGRESS.md

---

## 📌 常見任務快速參考

### 新增一個 ADR

1. 在 `docs/DECISIONS.md` 索引加一行
2. 複製底部的模板,編號 +1
3. 填寫背景、決策、理由、trade-off

### 完成一個 Step

1. 更新 `docs/PROGRESS.md` 的 checklist
2. 若有新決策,補 ADR
3. git commit(建議 message:`feat(step-X): 完成 XXX`)

### 有爭議性的技術選擇

1. **先停下**,不要急著寫 code
2. 討論 trade-off
3. 決議後寫進 DECISIONS.md
4. 再動手
