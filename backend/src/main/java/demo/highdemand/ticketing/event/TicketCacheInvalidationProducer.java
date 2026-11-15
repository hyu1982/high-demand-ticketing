package demo.highdemand.ticketing.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketCacheInvalidationProducer {

    private static final String TOPIC = "ticket-cache-invalidation";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketCacheInvalidationProducer(KafkaTemplate<String, Object> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(Long ticketId) {

        TicketCacheInvalidatedEvent event = new TicketCacheInvalidatedEvent(ticketId);

        System.out.println(
                "Publishing cache invalidation event for ticket: "
                        + ticketId
        );

//        kafkaTemplate.send(
//                TOPIC,
//                ticketId.toString(),
//                event
//        );

        try {
            kafkaTemplate.send(
                    TOPIC,
                    ticketId.toString(),
                    event
            ).get();

            System.out.println(
                    "Kafka accepted cache invalidation event for ticket: "
                            + ticketId
            );

        } catch (Exception e) {

            System.out.println(
                    "Kafka publish FAILED for ticket: "
                            + ticketId
            );

            e.printStackTrace();

            throw new RuntimeException(
                    "Failed to publish cache invalidation event",
                    e
            );
        }
    }
}