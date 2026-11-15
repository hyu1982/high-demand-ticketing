package demo.highdemand.ticketing.notification;


import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "Notification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UQ_Notification_EventId",
                        columnNames = "EventId"
                )
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EventId", nullable = false)
    private UUID eventId;

    @Column(name = "OrderId", nullable = false)
    private Long orderId;

    @Column(name = "CustomerEmail", nullable = false, length = 320)
    private String customerEmail;

    @Column(name = "SentAt", nullable = false)
    private LocalDateTime sentAt;

    protected Notification() {
    }

    public Notification(
            UUID eventId,
            Long orderId,
            String customerEmail) {

        this.eventId = eventId;
        this.orderId = orderId;
        this.customerEmail = customerEmail;
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}