package demo.highdemand.ticketing.validation;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class TicketSortValidator {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "ticketNumber",
            "status",
            "price",
            "eventName"
    );

    public void validate(Pageable pageable) {

        pageable.getSort().forEach(order -> {

            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "Sorting by '" + order.getProperty() +
                                "' is not supported"
                );
            }
        });
    }
}