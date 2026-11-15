package demo.highdemand.ticketing.controller;

import demo.highdemand.ticketing.entity.Order;
import demo.highdemand.ticketing.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService service)
    {
        this.orderService = service;
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/with-tickets")
    public List<Order> getAllOrdersWithTickets() {
        return orderService.getAllOrdersWithTickets();
    }
}
