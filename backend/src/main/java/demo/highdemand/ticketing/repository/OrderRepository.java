package demo.highdemand.ticketing.repository;

import demo.highdemand.ticketing.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
       SELECT o
       FROM Order o
       """)
    List<Order> findAllOrders();

    @Query("""
       SELECT o
       FROM Order o
       JOIN FETCH o.ticket
       """)
    List<Order> findAllWithTickets();

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

}
