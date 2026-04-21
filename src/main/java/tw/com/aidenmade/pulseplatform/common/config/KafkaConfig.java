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

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    /**
     * monitoring.events.raw — 8 partitions, 以 serviceId 為 key 確保同 service 有序。
     * replicas=1 因為 Phase 1 是單 broker。
     * 見 ADR-001、ADR-004。
     */
    @Bean
    public NewTopic monitoringEventsRaw() {
        return TopicBuilder.name("monitoring.events.raw")
                .partitions(8)
                .replicas(1)
                .build();
    }

    /**
     * 注入 Spring 管理的 ObjectMapper, 確保 Kafka 序列化與 HTTP 層使用相同設定
     * (例如 write-dates-as-timestamps=false,ADR-007)。
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(false);  // 不在 header 塞 Java class name
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
