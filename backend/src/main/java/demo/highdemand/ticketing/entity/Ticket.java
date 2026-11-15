package demo.highdemand.ticketing.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_number")
    private String ticketNumber;
    @Column(name = "ticket_status")
    private String status;

    @NotBlank
    @Column(name = "event_name")
    private String eventName;

    @NotBlank
    @Email
    @Column(name = "customer_email")
    private String customerEmail;
    private BigDecimal price;
    @Version
    private Long version;
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

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

    public void setEventName(String eventName){ this.eventName=eventName; }

    public String getEventName(){ return eventName; }

    public void setCustomerEmail(String customerEmail){ this.customerEmail = customerEmail; }

    public String getCustomerEmail(){ return customerEmail; }

    public BigDecimal getPrice() { return price; }

    public void setPrice(BigDecimal price){ this.price = price; }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
