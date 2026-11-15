package demo.highdemand.ticketing.event;

import demo.highdemand.ticketing.service.TicketCacheService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TicketCacheInvalidationConsumer {

    private static final String TOPIC = "ticket-cache-invalidation";

    private final TicketCacheService ticketCacheService;

    public TicketCacheInvalidationConsumer(TicketCacheService ticketCacheService) {

        this.ticketCacheService = ticketCacheService;
    }

    @KafkaListener(
            topics = TOPIC,
            groupId = "ticket-cache-invalidation-service",
            properties = {
                    "spring.json.value.default.type=demo.highdemand.ticketing.event.TicketCacheInvalidatedEvent"
            }
    )
    public void handle(
            TicketCacheInvalidatedEvent event) {

        System.out.println(
                ">>> CACHE INVALIDATION CONSUMER RECEIVED EVENT <<<"
        );

        System.out.println(
                "Ticket ID: " + event.ticketId()
        );

        String cacheKey =
                "ticket:" + event.ticketId();

        ticketCacheService.evict(cacheKey);

        System.out.println(
                "Invalidated cache: " + cacheKey
        );
    }
}
