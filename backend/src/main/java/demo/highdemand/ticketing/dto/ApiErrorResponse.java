package demo.highdemand.ticketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(
        description = "Standard API error response"
)
public class ApiErrorResponse {
    @Schema(
            description = "HTTP status code",
            example = "404"
    )
    private final int status;
    @Schema(
            description = "Human-readable description of the error",
            example = "Ticket with id 999 was not found"
    )
    private final String message;
    @Schema(
            description = "Validation errors by field. Null when the error is not field-specific.",
            example = "{\"customerEmail\":\"must be a valid email address\"}"
    )
    private final Map<String, String> errors;

    public ApiErrorResponse(
            int status,
            String message,
            Map<String, String> errors) {

        this.status = status;
        this.message = message;
        this.errors = errors;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
