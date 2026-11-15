package demo.highdemand.ticketing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class PatchTicketRequest {
    @NotBlank
    private String status;

    @NotNull
    @PositiveOrZero
    private BigDecimal price;

    public String getStatus() {
        return status;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
