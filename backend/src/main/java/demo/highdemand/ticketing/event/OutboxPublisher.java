package demo.highdemand.ticketing.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    //private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {

        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        LocalDateTime cutoff =
                LocalDateTime.now().minusMinutes(1);

        int released =
                outboxEventRepository.releaseStaleClaims(cutoff);

        if (released > 0) {
            System.out.println(
                    "Released " +
                            released +
                            " stale outbox event(s)."
            );
        }

        List<OutboxEvent> events =
                outboxEventRepository
                        .findTop100ByPublishedAtIsNullOrderByIdAsc();

        for (OutboxEvent event : events) {

            int claimed =
                    outboxEventRepository.claimEvent(
                            event.getId()
                    );

            if (claimed == 0) {
                System.out.println(
                        "Skipping outbox event " +
                                event.getEventId() +
                                " because it is already being processed."
                );

                continue;
            }

            System.out.println(
                    "Claimed outbox event: " +
                            event.getEventId()
            );

            try {

                Object eventPayload;
                String topic;

                switch (event.getEventType()) {

                    case "OrderCreatedEvent":

                        eventPayload =
                                objectMapper.readValue(
                                        event.getPayload(),
                                        OrderCreatedEvent.class
                                );

                        topic = "order-created";

                        break;

                    case "TicketCacheInvalidatedEvent":

                        eventPayload =
                                objectMapper.readValue(
                                        event.getPayload(),
                                        TicketCacheInvalidatedEvent.class
                                );

                        topic =
                                "ticket-cache-invalidation";

                        break;

                    default:

                        throw new IllegalArgumentException(
                                "Unsupported outbox event type: " +
                                        event.getEventType()
                        );
                }

                kafkaTemplate.send(
                        topic,
                        event.getPartitionKey(),
                        eventPayload
                ).get(5, TimeUnit.SECONDS);

                event.markPublished();

                outboxEventRepository.save(event);

            } catch (Exception e) {

                event.releasePublishing();

                outboxEventRepository.save(event);

                System.out.println(
                        "Released outbox event: " +
                                event.getEventId()
                );

                System.err.println(
                        "Failed to publish outbox event " +
                                event.getEventId() +
                                ": " +
                                e.getMessage()
                );
            }
        }
    }
}