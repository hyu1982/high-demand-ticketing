package demo.highdemand.ticketing.specification;

import demo.highdemand.ticketing.dto.TicketSearchCriteria;
import demo.highdemand.ticketing.entity.Ticket;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public class TicketSpecification {

    public static Specification<Ticket> hasStatus(String status) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        root.get("status"),
                        status
                );
    }

    public static Specification<Ticket> hasEventName(String eventName) {

//        return (root, query, criteriaBuilder) ->
//                criteriaBuilder.equal(
//                        root.get("eventName"),
//                        eventName
//                );
        String searchTerm = eventName.trim().toLowerCase(
                java.util.Locale.ROOT
        ) + "%";

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("eventName")),
                        searchTerm
                );
    }

    public static Specification<Ticket> priceLessThanOrEqualTo(
            BigDecimal maxPrice) {

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.lessThanOrEqualTo(
                        root.get("price"),
                        maxPrice
                );
    }

    public static Specification<Ticket> fromCriteria(
            TicketSearchCriteria criteria) {

//        Specification<Ticket> specification =
//                Specification.where((Specification<Ticket>) null);

        Specification<Ticket> specification =
                (root, query, criteriaBuilder) -> null;

        if (criteria.getStatus() != null
                && !criteria.getStatus().isBlank()) {

            specification = specification.and(
                    hasStatus(criteria.getStatus())
            );
        }

        if (criteria.getEventName() != null
                && !criteria.getEventName().isBlank()) {

            specification = specification.and(
                    hasEventName(criteria.getEventName())
            );
        }

        if (criteria.getMaxPrice() != null) {

            specification = specification.and(
                    priceLessThanOrEqualTo(
                            criteria.getMaxPrice()
                    )
            );
        }

        return specification;
    }


}
