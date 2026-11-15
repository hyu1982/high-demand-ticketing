package demo.highdemand.ticketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public class TicketResponse {

    @Schema(
            description = "Unique identifier of the ticket",
            example = "123"
    )
    private Long id;

    @Schema(
            description = "Unique ticket number",
            example = "TKT-2026-000123"
    )
    private String ticketNumber;

    @Schema(
            description = "Current lifecycle status of the ticket",
            example = "PENDING"
    )
    private String status;

    @Schema(
            description = "Ticket price",
            example = "149.99"
    )
    private BigDecimal price;

    @Schema(
            description = "Name of the entertainment event",
            example = "Disney Halloween Event"
    )
    private String eventName;

    @Schema(
            description = "Email address associated with the ticket",
            example = "guest@example.com"
    )
    private String customerEmail;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String ticketNumber) {
        this.ticketNumber = ticketNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }
}
