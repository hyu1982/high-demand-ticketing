package demo.highdemand.ticketing.event;

import java.util.UUID;

public record OrderCreatedEventV2(
        UUID eventId,
        Long orderId,
        Long ticketId,
        String customerEmail,
        String eventName,
        String customerName
) {}