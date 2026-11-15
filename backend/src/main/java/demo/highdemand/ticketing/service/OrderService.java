package demo.highdemand.ticketing.service;


import demo.highdemand.ticketing.entity.Order;
import demo.highdemand.ticketing.entity.Ticket;
import demo.highdemand.ticketing.event.OrderCreatedEvent;
import demo.highdemand.ticketing.event.OutboxEvent;
import demo.highdemand.ticketing.event.OutboxEventRepository;
import demo.highdemand.ticketing.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

//    public Order createOrder(Long ticketId, String customerEmail) {
//
//        Order order = new Order();
//
//        order.setTicketId(ticketId);
//        order.setCustomerEmail(customerEmail);
//        order.setStatus("CREATED");
//       // order.setCreatedAt(LocalDateTime.now());
//        order.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
//
//        //return orderRepository.save(order);
//
//        Order savedOrder = orderRepository.save(order);
//
//        return savedOrder;
//
//        //throw new RuntimeException("TEST: Force purchase rollback");
//    }
    public Order createOrder(Ticket ticket, String customerEmail, String idempotencyKey) {

        Order order = new Order();
        order.setTicket(ticket);
        order.setCustomerEmail(customerEmail);
        order.setStatus("CREATED");
        order.setCreatedAt(
                LocalDateTime.now(ZoneOffset.UTC)
        );
        order.setCustomerName("Test Customer");
        order.setIdempotencyKey(idempotencyKey);

        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                savedOrder.getId(),
                ticket.getId(),
                customerEmail,
                ticket.getEventName(),
                order.getCustomerName(),
                2
        );

        //orderEventProducer.publish(event);

        String payload;

        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize OrderCreatedEvent",
                    e
            );
        }

        OutboxEvent outboxEvent = new OutboxEvent(
                event.eventId(),
                "OrderCreatedEvent",
                event.orderId().toString(),
                payload
        );

        outboxEventRepository.save(outboxEvent);

        return savedOrder;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllOrders();
    }

    public List<Order> getAllOrdersWithTickets() {
        return orderRepository.findAllWithTickets();
    }
}