package demo.highdemand.ticketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class TicketSearchCriteria {

    @Schema(
            description = "Ticket status used to filter results",
            example = "PENDING"
    )
    @Pattern(
            regexp = "^(PENDING|SOLD|CANCELLED)?$",
            message = "Status must be PENDING, SOLD, CANCELLED, or blank."
    )
    private String status;

    @Schema(
            description = "Event name used to filter results; maximum 100 characters",
            example = "Magic Kingdom"
    )
    @Size(
            max = 100,
            message = "Event name cannot exceed 100 characters."
    )
    private String eventName;


    @Schema(
            description = "Maximum ticket price",
            example = "100.00"
    )
    @DecimalMin(
            value = "0.00",
            message = "Maximum price must be greater than or equal to zero."
    )
    private BigDecimal maxPrice;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }
}
