package demo.highdemand.ticketing.event;

import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        Long ticketId,
        String customerEmail,
        String eventName,
        String customerName,
        Integer version
) {
}