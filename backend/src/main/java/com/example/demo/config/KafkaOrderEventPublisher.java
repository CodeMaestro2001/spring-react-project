package com.example.demo.config;

import com.example.demo.service.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes only after the database transaction commits, so consumers never see rolled-back orders. */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaOrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String orderTopic;

    public KafkaOrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                                    @org.springframework.beans.factory.annotation.Value("${app.kafka.order-topic}") String orderTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderTopic = orderTopic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OrderEvent event) {
        kafkaTemplate.send(orderTopic, event.orderId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Kafka publish failed for order event orderId={} type={}",
                                event.orderId(), event.eventType(), error);
                    } else {
                        log.info("Kafka order event published orderId={} type={} topic={} partition={} offset={}",
                                event.orderId(), event.eventType(), orderTopic,
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
    }
}
