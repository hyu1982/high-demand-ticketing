package demo.highdemand.ticketing.repository;

import demo.highdemand.ticketing.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
       UPDATE Ticket t
       SET t.status = 'SOLD'
       WHERE t.id = :id
       AND t.status = 'PENDING'
       """)
    int markAsSoldIfPending(@Param("id") Long id);

    Optional<Ticket> findByIdempotencyKey(String idempotencyKey);

    @Query("""
       SELECT t
       FROM Ticket t
       WHERE t.status = :status
       """)
    List<Ticket> findByStatus(@Param("status") String status);

    Page<Ticket> findByStatus(
            String status,
            Pageable pageable);

    @Query("""
       SELECT t
       FROM Ticket t
       WHERE t.status = :status
       AND t.price <= :maxPrice
       """)
    List<Ticket> findAvailableTickets(
            @Param("status") String status,
            @Param("maxPrice") BigDecimal maxPrice);

    @Query(value = """
       SELECT *
       FROM Tickets
       WHERE EventName = :eventName
       """, nativeQuery = true)
    List<Ticket> findByEventNameNative(
            @Param("eventName") String eventName);
}
