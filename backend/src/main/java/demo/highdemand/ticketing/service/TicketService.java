package demo.highdemand.ticketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo.highdemand.ticketing.dto.CreateTicketRequest;
import demo.highdemand.ticketing.dto.TicketDto;
import demo.highdemand.ticketing.dto.TicketResponse;
import demo.highdemand.ticketing.dto.TicketSearchCriteria;
import demo.highdemand.ticketing.entity.Order;
import demo.highdemand.ticketing.entity.Ticket;
import demo.highdemand.ticketing.event.OutboxEvent;
import demo.highdemand.ticketing.event.OutboxEventRepository;
import demo.highdemand.ticketing.event.TicketCacheInvalidatedEvent;
import demo.highdemand.ticketing.event.TicketCacheInvalidationProducer;
import demo.highdemand.ticketing.exception.TicketConflictException;
import demo.highdemand.ticketing.exception.TicketNotFoundException;
import demo.highdemand.ticketing.repository.OrderRepository;
import demo.highdemand.ticketing.repository.TicketRepository;
import demo.highdemand.ticketing.specification.TicketSpecification;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final TicketCacheService ticketCacheService;
    private final OutboxEventRepository outboxEventRepository;
    private static final long LOCK_RETRY_DELAY_MS = 50;
    private static final int MAX_LOCK_RETRIES = 10;
    private static final long LOCK_BASE_DELAY_MS = 50;
    private static final long LOCK_MAX_DELAY_MS = 500;
    private static final long LOCK_RETRY_BUDGET_MS = 500;
    private final TicketCacheInvalidationProducer ticketCacheInvalidationProducer;

    //constructor DI
    public TicketService(TicketRepository ticketRepository, OrderService orderService, ObjectMapper objectMapper, TicketCacheService ticketCacheService, TicketCacheInvalidationProducer ticketCacheInvalidationProducer, OutboxEventRepository outboxEventRepository, OrderRepository orderRepository) {

        this.ticketRepository = ticketRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.ticketCacheService = ticketCacheService;
        this.ticketCacheInvalidationProducer = ticketCacheInvalidationProducer;
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
    }

    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN')")
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {

        Ticket ticket = new Ticket();

        ticket.setEventName(request.getEventName());
        ticket.setCustomerEmail(request.getCustomerEmail());
        ticket.setPrice(BigDecimal.valueOf(20));

        Ticket savedTicket = ticketRepository.save(ticket);

        return toTicketResponse(savedTicket);
    }

    @PreAuthorize("@ticketSecurity.canAccess(#id, authentication)")
    public TicketResponse getTicket(Long id) {

        String cacheKey = "ticket:" + id;
        String lockKey = "lock:ticket:" + id;

        TicketCacheService.CacheResult cacheResult =
                ticketCacheService.get(cacheKey);

        if (cacheResult.status() ==
                TicketCacheService.CacheStatus.HIT) {

            System.out.println("Redis cache hit: " + cacheKey);

            try {
                return objectMapper.readValue(
                        cacheResult.value(),
                        TicketResponse.class
                );

            } catch (JsonProcessingException e) {

                throw new RuntimeException(
                        "Failed to deserialize cached ticket",
                        e
                );
            }
        }

        if (cacheResult.status() ==
                TicketCacheService.CacheStatus.UNAVAILABLE) {

            System.out.println(
                    "Redis unavailable. Loading ticket directly from SQL."
            );

            Ticket ticket = ticketRepository.findById(id)
                    .orElseThrow(() ->
                            new TicketNotFoundException(
                                    "Ticket with id " + id +
                                            " was not found"
                            ));

            return toTicketResponse(ticket);
        }

        System.out.println("Redis cache miss: " + cacheKey);

        //try and get distributed lock
        TicketCacheService.LockResult lockResult =
                ticketCacheService.tryAcquireLock(lockKey);

        if (lockResult.status() ==
                TicketCacheService.LockStatus.UNAVAILABLE) {

            System.out.println(
                    "Redis unavailable during lock acquisition. " +
                            "Loading ticket directly from SQL."
            );

            Ticket ticket = ticketRepository.findById(id)
                    .orElseThrow(() ->
                            new TicketNotFoundException(
                                    "Ticket with id " + id +
                                            " was not found"
                            ));

            return toTicketResponse(ticket);
        }

        if (lockResult.status() == TicketCacheService.LockStatus.ACQUIRED) {

            String lockValue = lockResult.lockValue();

            System.out.println("Distributed Lock acquired: " + lockKey);

//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                throw new RuntimeException(
//                        "Thread interrupted while testing lock contention",
//                        e
//                );
//            }

            Ticket ticket;

            try {
                ticket = ticketRepository.findById(id)
                        .orElseThrow(() ->
                                new TicketNotFoundException(
                                        "Ticket with id " + id +
                                                " was not found"
                                ));

                TicketResponse ticketResponse =
                        toTicketResponse(ticket);

                ticketCacheService.putTicket(
                        cacheKey,
                        ticketResponse
                );

                return ticketResponse;

            } finally {
                ticketCacheService.releaseLock(
                        lockKey,
                        lockValue
                );
            }
        }

        long retryStartTime = System.nanoTime();
        for (int attempt = 0; attempt < MAX_LOCK_RETRIES; attempt++) {

            try {
                //Thread.sleep(LOCK_RETRY_DELAY_MS);
                long elapsedTimeMs =
                        (System.nanoTime() - retryStartTime)
                                / 1_000_000;
                long remainingBudgetMs =
                        LOCK_RETRY_BUDGET_MS - elapsedTimeMs;

                //System.out.println("Remaining: " + remainingBudgetMs + "ms");

                if (remainingBudgetMs <= 0) {

                    System.out.println(
                            "Lock retry budget exhausted after "
                                    + elapsedTimeMs
                                    + " ms. " +
                                    "Loading ticket directly from SQL."
                    );

                    Ticket ticket = ticketRepository.findById(id)
                            .orElseThrow(() ->
                                    new TicketNotFoundException(
                                            "Ticket with id " + id +
                                                    " was not found"
                                    ));

                    return toTicketResponse(ticket);
                }

                long exponentialDelay =
                        LOCK_BASE_DELAY_MS * (1L << attempt);

                long boundedDelay =
                        Math.min(
                                exponentialDelay,
                                LOCK_MAX_DELAY_MS
                        );

                long jitter =
                        ThreadLocalRandom.current()
                                .nextLong(0, LOCK_BASE_DELAY_MS + 1);

                long delay =
                        Math.min(
                                boundedDelay + jitter,
                                LOCK_MAX_DELAY_MS
                        );

                delay = Math.min(
                        delay,
                        remainingBudgetMs
                );

                //System.out.println("Sleep ms: " + delay);
                Thread.sleep(delay);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for cache rebuild", e);
            }

            TicketCacheService.CacheResult retryCacheResult =
                    ticketCacheService.get(cacheKey);

            if (retryCacheResult.status() ==
                    TicketCacheService.CacheStatus.HIT) {

                try {
                    System.out.println(
                            "Redis cache hit after waiting: " +
                                    cacheKey +
                                    " on attempt " +
                                    attempt
                    );

                    return objectMapper.readValue(
                            retryCacheResult.value(),
                            TicketResponse.class
                    );

                } catch (JsonProcessingException e) {

                    throw new RuntimeException(
                            "Failed to deserialize cached ticket",
                            e
                    );
                }
            }

            if (retryCacheResult.status() ==
                    TicketCacheService.CacheStatus.UNAVAILABLE) {

                System.out.println(
                        "Redis became unavailable while waiting. " +
                                "Loading ticket directly from SQL."
                );

                Ticket ticket = ticketRepository.findById(id)
                        .orElseThrow(() ->
                                new TicketNotFoundException(
                                        "Ticket with id " + id +
                                                " was not found"
                                ));

                return toTicketResponse(ticket);
            }

            System.out.println(
                    "Cache still missing: " +
                            cacheKey +
                            " attempt " +
                            attempt
            );
        }

//        throw new RuntimeException(
//                "Cache rebuild did not complete " +
//                        "within retry window"
//        );

        System.out.println(
                "Cache rebuild did not complete within retry window. " +
                        "Loading ticket directly from SQL."
        );

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(
                                "Ticket with id " + id +
                                        " was not found"
                        ));

        return toTicketResponse(ticket);
    }

    @PreAuthorize("hasAnyRole('SUPPORT','ADMIN')")
    @Transactional
    public TicketResponse cancelTicket(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(
                                "Ticket with id " + id + " was not found"
                        ));

        if ("SOLD".equals(ticket.getStatus())) {
            throw new TicketConflictException(
                    "Ticket with id " + id + " cannot be cancelled because it is SOLD"
            );
        }

        ticket.setStatus("CANCELLED");

        Ticket savedTicket = ticketRepository.save(ticket);


        return toTicketResponse(savedTicket);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    private TicketResponse toTicketResponse(Ticket ticket) {

        return getTicketResponse(ticket);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    @Transactional
    public TicketResponse testConcurrentPurchase(Long id) {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(
                                "Ticket with id " + id + " was not found"
                        ));

        if ("SOLD".equals(ticket.getStatus())) {
            throw new TicketConflictException(
                    "Ticket with id " + id + " is already SOLD"
            );
        }

        // Simulate some processing time so another transaction
        // has an opportunity to read the same ticket.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }

        ticket.setStatus("SOLD");

        Ticket savedTicket = ticketRepository.save(ticket);

        return toTicketResponse(savedTicket);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    @Transactional
    public TicketResponse testPessimisticPurchase(Long id) {

        Ticket ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(
                                "Ticket with id " + id + " was not found"
                        ));

        if ("SOLD".equals(ticket.getStatus())) {
            throw new TicketConflictException(
                    "Ticket with id " + id + " is already SOLD"
            );
        }

        // Simulate payment / processing time.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }

        ticket.setStatus("SOLD");

        Ticket savedTicket = ticketRepository.save(ticket);

        return toTicketResponse(savedTicket);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    @Transactional
    public TicketResponse purchaseTicketAtomically(
            Long id,
            String idempotencyKey) {

        Optional<Ticket> existingTicket =
                ticketRepository.findByIdempotencyKey(idempotencyKey);

        if (existingTicket.isPresent()) {
            return toTicketResponse(existingTicket.get());
        }

        int updatedRows =
                ticketRepository.markAsSoldIfPending(id);

        if (updatedRows == 0) {
            throw new TicketConflictException(
                    "Ticket with id " + id +
                            " is no longer available for purchase"
            );
        }

        ticketCacheInvalidationProducer.publish(id);

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() ->
                        new TicketNotFoundException(
                                "Ticket with id " + id + " was not found"
                        ));

        ticket.setIdempotencyKey(idempotencyKey);

        Ticket savedTicket = ticketRepository.save(ticket);

        return toTicketResponse(savedTicket);
    }

    @PreAuthorize("@ticketSecurity.canPurchase(authentication)")
    @Transactional
    public Order purchaseTicket(Long ticketId, String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        Optional<Order> existingOrder =
                orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
            Order order = existingOrder.get();

            if (!order.getTicket().getId().equals(ticketId)) {
                throw new TicketConflictException(
                        "Idempotency key was already used for a different ticket"
                );
            }

            return order;
        }

        int updatedRows =
                ticketRepository.markAsSoldIfPending(ticketId);

        if (updatedRows == 0) {
            throw new TicketConflictException(
                    "Ticket with id " + ticketId +
                            " is no longer available for purchase"
            );
        }

        Ticket ticket =
                ticketRepository.findById(ticketId)
                        .orElseThrow(() ->
                                new TicketNotFoundException(
                                        "Ticket with id " + ticketId +
                                                " was not found"
                                ));

        Order order =
                orderService.createOrder(
                        ticket,
                        ticket.getCustomerEmail(),
                        idempotencyKey
                );

        TicketCacheInvalidatedEvent event = new TicketCacheInvalidatedEvent(
                        ticketId
                );

        String payload;

        try {
            payload =  objectMapper.writeValueAsString(event);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException(
                    "Failed to serialize TicketCacheInvalidatedEvent",
                    e
            );
        }

        OutboxEvent outboxEvent =
                new OutboxEvent(
                        UUID.randomUUID(),
                        "TicketCacheInvalidatedEvent",
                        ticketId.toString(),
                        payload
                );

        outboxEventRepository.save(outboxEvent);

        return order;
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    public List<Ticket> getTicketsByEventNameNative(String eventName) {
        return ticketRepository.findByEventNameNative(eventName);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    public Page<TicketResponse> getTicketsByStatus(
            String status,
            Pageable pageable) {

        return ticketRepository
                .findByStatus(status, pageable)
                .map(this::toTicketResponse);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    public List<Ticket> searchTickets(String status) {

        Specification<Ticket> specification =
                TicketSpecification.hasStatus(status);

        return ticketRepository.findAll(specification);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    public List<Ticket> searchCustomSpecification(){
        Specification<Ticket> specification =
                TicketSpecification.hasStatus("PENDING")
                        .and(
                                TicketSpecification.hasEventName(
                                        "Magic Kingdom"
                                )
                        )
                        .and(
                                TicketSpecification.priceLessThanOrEqualTo(
                                        new BigDecimal("200")
                                )
                        );
        return ticketRepository.findAll(specification);
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    public Page<Ticket> searchTickets(
            TicketSearchCriteria criteria,
            Pageable pageable) {

        Specification<Ticket> specification =
                TicketSpecification.fromCriteria(criteria);

        return ticketRepository.findAll(
                specification,
                pageable
        );
    }

    @PreAuthorize("hasAnyRole('USER', 'SUPPORT', 'ADMIN')")
    public Page<TicketDto> searchTicketsDto(
            TicketSearchCriteria criteria,
            Pageable pageable) {

        Specification<Ticket> specification =
                TicketSpecification.fromCriteria(criteria);

        Page<Ticket> tickets =
                ticketRepository.findAll(
                        specification,
                        pageable
                );

        return tickets.map(this::toDto);
    }

    private TicketDto toDto(Ticket ticket) {

        TicketDto dto = new TicketDto();

        dto.setId(ticket.getId());
        dto.setTicketNumber(ticket.getTicketNumber());
        dto.setStatus(ticket.getStatus());
        dto.setPrice(ticket.getPrice());
        dto.setEventName(ticket.getEventName());
        dto.setCustomerEmail(ticket.getCustomerEmail());

        return dto;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public String getAdminInformation() {
        return "Sensitive ticket administration information";
    }

    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public String getSupportInformation(Long ticketId) {

        return "Support information for ticket " + ticketId;
    }

    public boolean isTicketOwner(Long ticketId, Authentication authentication) {
        Ticket ticket = ticketRepository
                .findById(ticketId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Ticket not found"
                        )
                );

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return false;
        }

        String email = jwtAuthentication .getToken()
                        .getClaimAsString("email");

        return ticket.getCustomerEmail()
                .equalsIgnoreCase(email);
    }

    @PreAuthorize("@ticketSecurity.canAccess(#ticketId, authentication)")
    public TicketResponse updateTicket(
            Long ticketId,
            String status,
            BigDecimal price) {

        Ticket ticket = ticketRepository
                .findById(ticketId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Ticket not found"
                        )
                );

        ticket.setStatus(status);
        ticket.setPrice(price);

        Ticket savedTicket = ticketRepository.save(ticket);

        return getTicketResponse(savedTicket);
    }

    @NonNull
    private TicketResponse getTicketResponse(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setTicketNumber(ticket.getTicketNumber());
        response.setStatus(ticket.getStatus());
        response.setPrice(ticket.getPrice());
        response.setEventName(ticket.getEventName());
        response.setCustomerEmail(ticket.getCustomerEmail());

        return response;
    }

    @PreAuthorize("@ticketSecurity.canAccess(#ticketId, authentication)")
    public TicketResponse patchTicket(
            Long ticketId,
            String status,
            BigDecimal price) {

        Ticket ticket = ticketRepository
                .findById(ticketId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Ticket not found"
                        )
                );

        ticket.setStatus(status);
        ticket.setPrice(price);

        Ticket savedTicket = ticketRepository.save(ticket);

        return toTicketResponse(savedTicket);
    }
}
