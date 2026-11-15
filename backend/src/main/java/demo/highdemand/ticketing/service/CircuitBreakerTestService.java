package demo.highdemand.ticketing.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class CircuitBreakerTestService {

    private boolean shouldFail = true;

    @CircuitBreaker(name = "ticketBackend")
    public String callTicketBackend() {

        if (shouldFail) {
            throw new RuntimeException("Simulated ticket backend failure");
        }

        return "Ticket backend is healthy";
    }

    public void recover() {
        shouldFail = false;
    }
}