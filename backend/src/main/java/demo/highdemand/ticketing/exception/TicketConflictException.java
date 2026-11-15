package demo.highdemand.ticketing.exception;

public class TicketConflictException extends RuntimeException {

    public TicketConflictException(String message) {
        super(message);
    }
}
