package tw.com.aidenmade.pulseplatform.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import tw.com.aidenmade.pulseplatform.ingestion.EventRequest;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfig {

    /**
     * 注入 Spring ObjectMapper,確保 Instant 反序列化與 HTTP 層一致(ADR-007)。
     * setUseTypeHeaders(false):因為 producer 端沒有加 type header。
     */
    @Bean
    public ConsumerFactory<String, EventRequest> consumerFactory(
            KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        JsonDeserializer<EventRequest> valueDeserializer =
                new JsonDeserializer<>(EventRequest.class, objectMapper);
        valueDeserializer.setUseTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    /**
     * concurrency=4:8 partitions / 2 = 4 threads,每條執行緒負責 2 個 partition。
     * AckMode.MANUAL_IMMEDIATE:呼叫 ack.acknowledge() 後立即 commit offset。
     * 見 ADR-005。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventRequest> kafkaListenerContainerFactory(
            ConsumerFactory<String, EventRequest> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, EventRequest>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(4);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
