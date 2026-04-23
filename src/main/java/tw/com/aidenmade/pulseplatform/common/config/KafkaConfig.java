package tw.com.aidenmade.pulseplatform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/**
 * Kafka 相關的 Spring Bean 設定。
 *
 * <p>本類別負責三件事：
 * <ol>
 *   <li>宣告需要自動建立的 Kafka topic（{@link NewTopic}）</li>
 *   <li>設定 Producer 如何將 Java 物件序列化成 JSON 送出（{@link ProducerFactory}）</li>
 *   <li>提供全域可注入的發送工具 {@link KafkaTemplate}</li>
 * </ol>
 *
 * <p>連線位址等基礎設定統一由 {@code application.yml} 的
 * {@code spring.kafka.*} 控制，此處不重複定義。
 */
@Configuration(proxyBeanMethods = false) // proxyBeanMethods=false：@Bean 方法之間不走 CGLIB 代理，效能較佳
public class KafkaConfig {

    /**
     * 宣告 {@code monitoring.events.raw} topic 的規格。
     *
     * <p>Spring Boot 啟動時，內建的 {@code KafkaAdmin} 會自動掃描所有
     * {@link NewTopic} bean，並在 Kafka broker 上建立尚未存在的 topic。
     * 開發者不需要手動執行任何指令。
     *
     * <ul>
     *   <li>{@code partitions=8}：訊息以 serviceId 為 partition key，
     *       確保同一個 service 的事件依序被消費（見 ADR-004）</li>
     *   <li>{@code replicas=1}：Phase 1 為單一 broker 的本機環境，
     *       production 環境應改為 3 以確保高可用（見 ADR-001）</li>
     * </ul>
     */
    @Bean
    public NewTopic monitoringEventsRaw() {
        return TopicBuilder.name("monitoring.events.raw")
                .partitions(8)
                .replicas(1)
                .build();
    }

    /**
     * 自訂 Kafka Producer 的工廠，控制訊息如何被序列化後送往 broker。
     *
     * <p>Producer 需要兩個序列化器：
     * <ul>
     *   <li><b>Key 序列化器</b>：用 {@link StringSerializer}，因為 key 固定是 serviceId 字串</li>
     *   <li><b>Value 序列化器</b>：用 {@link JsonSerializer}，將任意 Java 物件轉成 JSON byte array</li>
     * </ul>
     *
     * <p>這裡刻意注入 Spring 容器管理的 {@link ObjectMapper}（而非 new 一個新的），
     * 目的是讓 Kafka 與 HTTP API 層共用同一份 JSON 設定
     * （例如：日期格式輸出為 ISO-8601 字串而非數字時間戳，見 ADR-007）。
     *
     * @param kafkaProperties Spring Boot 自動讀取 {@code application.yml} 產生的 Kafka 設定物件
     * @param objectMapper    Spring 容器中已設定好的 JSON 工具
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {

        // 從 application.yml 的 spring.kafka.producer.* 讀取 bootstrap-servers、acks 等基礎設定
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);

        // 建立 JSON value 序列化器，並綁定共用的 ObjectMapper
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);

        // 停用 type header：預設 JsonSerializer 會在 Kafka message header 中夾帶 Java class name，
        // 但 consumer 端不一定是 Java，也不應依賴 producer 的類別名稱，故關閉此行為
        valueSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    /**
     * 全域共用的 Kafka 發送工具。
     *
     * <p>{@link KafkaTemplate} 是 Spring Kafka 對 Kafka Producer API 的封裝，
     * 提供更簡潔的 {@code send(topic, key, value)} 介面，並整合 Spring 的交易與錯誤處理機制。
     *
     * <p>注入上方自訂的 {@link ProducerFactory}，因此自動繼承其序列化與 ObjectMapper 設定。
     * 其他類別需要發送訊息時，直接 {@code @Autowired KafkaTemplate<String, Object>} 即可使用。
     *
     * @param producerFactory 上方宣告的自訂工廠
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
