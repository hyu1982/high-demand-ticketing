package demo.highdemand.ticketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public class CreateTicketRequest {

    @Schema(
            description = "Name of the entertainment event",
            example = "Backstage Pass Event"
    )
    @NotBlank(message = "Event name is required")
    private String eventName;

    @Schema(
            description = "Email address of the customer receiving the ticket",
            example = "guest@example.com"
    )
    @NotBlank(message = "Customer email is required")
    @Email(message = "Customer email must be a valid email address")
    private String customerEmail;

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