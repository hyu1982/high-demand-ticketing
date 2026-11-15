package demo.highdemand.ticketing.service;

import demo.highdemand.ticketing.entity.Ticket;
import demo.highdemand.ticketing.repository.TicketRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("ticketSecurity")
public class TicketSecurity {

    private final TicketRepository ticketRepository;

    public TicketSecurity(
            TicketRepository ticketRepository) {

        this.ticketRepository = ticketRepository;
    }

    public boolean isOwner(
            Long ticketId,
            Authentication authentication) {

        Ticket ticket = ticketRepository
                .findById(ticketId)
                .orElse(null);

        if (ticket == null) {
            return false;
        }

        if (!(authentication
                instanceof JwtAuthenticationToken jwtAuthentication)) {

            return false;
        }

        String email =
                jwtAuthentication
                        .getToken()
                        .getClaimAsString("email");

        return ticket.getCustomerEmail()
                .equalsIgnoreCase(email);
    }

    public boolean canAccess(
            Long ticketId,
            Authentication authentication) {

        if (authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_SUPPORT")
                                || authority.getAuthority().equals("ROLE_ADMIN"))) {

            return true;
        }

        return isOwner(ticketId, authentication);
    }

    public boolean canPurchase(
            Authentication authentication) {

        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_USER")
                                || authority.getAuthority().equals("ROLE_SUPPORT")
                                || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
