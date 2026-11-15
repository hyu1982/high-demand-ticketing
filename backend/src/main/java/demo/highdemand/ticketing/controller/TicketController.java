package demo.highdemand.ticketing.controller;

import demo.highdemand.ticketing.dto.*;
import demo.highdemand.ticketing.dto.*;
import demo.highdemand.ticketing.entity.Order;
import demo.highdemand.ticketing.entity.Ticket;
import demo.highdemand.ticketing.service.TicketService;
import demo.highdemand.ticketing.validation.TicketSortValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@SecurityRequirement(name = "bearerAuth")
@Tag(
        name = "Tickets",
        description = "Operations for managing entertainment tickets"
)
public class TicketController {
    //DI call
    private final TicketService ticketService;
    private final TicketSortValidator sortValidator;

    public TicketController(TicketService ticketService, TicketSortValidator validator) {
        this.ticketService = ticketService;
        this.sortValidator = validator;
    }

    @GetMapping
    @Operation(
            summary = "Search tickets",
            description = """
                Retrieves tickets using optional filtering,
                pagination, and sorting.

                Pagination defaults to 20 records per page and
                is limited to a maximum of 100 records per page.
                """
    )
    @Parameters({
            @Parameter(
                    name = "page",
                    description = "Zero-based page number",
                    example = "0"
            ),
            @Parameter(
                    name = "size",
                    description = "Number of records per page. Maximum is 100.",
                    example = "20"
            ),
            @Parameter(
                    name = "sort",
                    description = "Sort field and direction. Can be specified multiple times.",
                    example = "price,asc"
            )
    })
    public Page<TicketDto> getTickets(
            @Valid @ModelAttribute TicketSearchCriteria criteria,
        Pageable pageable) {

        sortValidator.validate(pageable);

    return ticketService.searchTicketsDto(
            criteria,
            pageable
    );
}

    @Operation(
            summary = "Get a ticket",
            description = "Retrieves a ticket by its ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ticket found"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ticket not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping("/{id}")
    public TicketResponse getTicket(@PathVariable Long id) {
        return ticketService.getTicket(id);
    }

    @Operation(
            summary = "Create a ticket",
            description = "Creates a new ticket."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Ticket created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid ticket data"
            )
    })
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request) {

        TicketResponse response = ticketService.createTicket(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "Cancel a ticket",
            description = "Cancels a ticket that is eligible for cancellation."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ticket cancelled successfully"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ticket not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ticket cannot be cancelled in its current state",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @PostMapping("/{id}/cancel")
    public TicketResponse cancelTicket(@PathVariable Long id) {
        return ticketService.cancelTicket(id);
    }

    @PostMapping("/{id}/test-concurrent-purchase")
    public TicketResponse testConcurrentPurchase(@PathVariable Long id) {
        return ticketService.testConcurrentPurchase(id);
    }

    @PostMapping("/{id}/test-pessimistic-purchase")
    public TicketResponse testPessimisticPurchase(@PathVariable Long id) {
        return ticketService.testPessimisticPurchase(id);
    }

    @PostMapping("/{id}/test-atomic-purchase")
    public TicketResponse testAtomicPurchase(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        return ticketService.purchaseTicketAtomically(
                id,
                idempotencyKey
        );
    }

    @Operation(
            summary = "Purchase a ticket",
            description = "Purchases an available ticket and creates an order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Ticket purchased and order created"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ticket not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Ticket is no longer available for purchase",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @PostMapping("/{id}/purchase")
//    public ResponseEntity<Order> purchaseTicket(@PathVariable Long id) {
//        Order order = ticketService.purchaseTicket(id);
//
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(order);
//    }
    public ResponseEntity<Order> purchaseTicket(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        Order order = ticketService.purchaseTicket(id, idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(order);
    }

    @GetMapping("/event-native/{eventName}")
    public List<Ticket> getTicketsByEventNameNative(
            @PathVariable String eventName) {

        return ticketService.getTicketsByEventNameNative(eventName);
    }

    @GetMapping("/status/{status}/page")
    public Page<TicketResponse> getTicketsByStatus(
            @PathVariable String status,
            Pageable pageable) {

        return ticketService.getTicketsByStatus(
                status,
                pageable);
    }

    @GetMapping("/specification")
    public List<Ticket> getSpecificationTest(){
        return ticketService.searchCustomSpecification();
    }

    @GetMapping("/search")
    public Page<TicketDto> searchTickets(TicketSearchCriteria criteria, Pageable pageable) {

        return ticketService.searchTicketsDto(
                criteria,
                pageable
        );
    }

    @PutMapping("/{id}")
    public TicketResponse updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request) {

        return ticketService.updateTicket(
                id,
                request.getStatus(),
                request.getPrice()
        );
    }

    @PatchMapping("/{id}")
    public TicketResponse patchTicket(
            @PathVariable Long id,
            @Valid @RequestBody PatchTicketRequest request) {

        return ticketService.patchTicket(
                id,
                request.getStatus(),
                request.getPrice()
        );
    }
}
