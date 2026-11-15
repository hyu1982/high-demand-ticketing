package demo.highdemand.ticketing.event;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UQ_OutboxEvent_EventId",
                        columnNames = "event_id"
                )
        }
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "publishing", nullable = false)
    private boolean publishing;

    @Column(name = "publishing_started_at")
    private LocalDateTime publishingStartedAt;

    @Column(name = "partition_key")
    private String partitionKey;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            UUID eventId,
            String eventType,
            String partitionKey,
            String payload) {

        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
        this.publishing = false;
        this.partitionKey = partitionKey;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }

    public boolean isPublishing() {
        return publishing;
    }

    public void markPublishing() {
        this.publishing = true;
        this.publishingStartedAt = LocalDateTime.now();
    }

    public void releasePublishing() {
        this.publishing = false;
        this.publishingStartedAt = null;
    }

    public LocalDateTime getPublishingStartedAt() {
        return publishingStartedAt;
    }

    public String getPartitionKey() {
        return partitionKey;
    }
}