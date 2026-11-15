package demo.highdemand.ticketing.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DltReplayService {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public DltReplayService(
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    public void replay(OrderCreatedEvent event) {

        kafkaTemplate.send(
                "order-created",
                event.orderId().toString(),
                event
        );
    }
}