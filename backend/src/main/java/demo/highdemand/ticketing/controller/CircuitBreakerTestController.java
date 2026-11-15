package demo.highdemand.ticketing.controller;

import demo.highdemand.ticketing.service.CircuitBreakerTestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CircuitBreakerTestController {

    private final CircuitBreakerTestService service;

    public CircuitBreakerTestController(CircuitBreakerTestService service) {
        this.service = service;
    }

    @GetMapping("/api/test/circuit-breaker")
    public String testCircuitBreaker() {
        return service.callTicketBackend();
    }

    @GetMapping("/api/test/circuit-breaker/recover")
    public String recover() {
        service.recover();
        return "Ticket backend has been marked healthy";
    }
}