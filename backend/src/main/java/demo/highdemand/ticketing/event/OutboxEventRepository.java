package demo.highdemand.ticketing.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;



public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByIdAsc();

    @Transactional
    @Modifying
    @Query("""
    UPDATE OutboxEvent o
    SET o.publishing = true,
    o.publishingStartedAt = CURRENT_TIMESTAMP
    WHERE o.id = :id
      AND o.publishedAt IS NULL
      AND o.publishing = false
""")
    int claimEvent(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("""
    UPDATE OutboxEvent o
    SET o.publishing = false,
        o.publishingStartedAt = null
    WHERE o.publishedAt IS NULL
      AND o.publishing = true
      AND o.publishingStartedAt < :cutoff
""")
    int releaseStaleClaims(@Param("cutoff") LocalDateTime cutoff);


    /* @Modifying
    @Query(value = """
    UPDATE outbox_events AS o
    SET publishing = false,
        publishing_started_at = NULL
    WHERE o.published_at IS NULL
      AND o.publishing = true
      AND o.publishing_started_at < :cutoff
    """, nativeQuery = true)
    int releaseStalePublishingEvents(
            @Param("cutoff") LocalDateTime cutoff
    );
    */
}