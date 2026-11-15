package demo.highdemand.ticketing.service;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    /*
    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private RedisTemplate redisTemplate;

    @Test
    void cancelTicket_WhenPending_CancelsTicket() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService,
                        redisTemplate
                );

        // Act
        TicketResponse response = ticketService.cancelTicket(1L);

        // Assert
        assertEquals("CANCELLED", response.getStatus());

        verify(ticketRepository).findById(1L);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void cancelTicket_WhenSold_ThrowsTicketConflictException() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("SOLD");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        assertThrows(
                TicketConflictException.class,
                () -> ticketService.cancelTicket(1L)
        );

        // Verify
        verify(ticketRepository).findById(1L);

        verify(ticketRepository, never())
                .save(ticket);
    }

    @Test
    void cancelTicket_WhenTicketDoesNotExist_ThrowsTicketNotFoundException() {

        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.cancelTicket(999L)
        );

        verify(ticketRepository).findById(999L);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void cancelTicket_WhenTicketDoesNotExist_ReturnsCorrectErrorMessage() {

        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketNotFoundException exception = assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.cancelTicket(999L)
        );

        assertEquals(
                "Ticket with id 999 was not found",
                exception.getMessage()
        );

        verify(ticketRepository).findById(999L);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void purchaseTicket_WhenPending_CreatesOrder() {

        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setCustomerEmail("customer@example.com");

        Order order = new Order();
        order.setTicket(ticket);
        order.setCustomerEmail("customer@example.com");
        order.setStatus("CREATED");

        when(ticketRepository.markAsSoldIfPending(1L))
                .thenReturn(1);

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(orderService.createOrder(ticket, "customer@example.com"))
                .thenReturn(order);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        Order result = ticketService.purchaseTicket(1L);

        assertEquals(order, result);

        verify(ticketRepository).markAsSoldIfPending(1L);
        verify(ticketRepository).findById(1L);
        verify(orderService).createOrder(ticket, "customer@example.com");
    }

    @Test
    void purchaseTicket_WhenTicketNotAvailable_ThrowsTicketConflictException() {

        when(ticketRepository.markAsSoldIfPending(1L))
                .thenReturn(0);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketConflictException exception = assertThrows(
                TicketConflictException.class,
                () -> ticketService.purchaseTicket(1L)
        );

        assertEquals(
                "Ticket with id 1 is no longer available for purchase",
                exception.getMessage()
        );

        verify(ticketRepository).markAsSoldIfPending(1L);
        verify(ticketRepository, never()).findById(1L);
        verify(orderService, never()).createOrder(any(), any());
    }

    @Test
    void purchaseTicket_WhenTicketCannotBeFoundAfterPurchase_ThrowsTicketNotFoundException() {

        // Arrange
        when(ticketRepository.markAsSoldIfPending(999L))
                .thenReturn(1);

        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        // Act & Assert
        TicketNotFoundException exception = assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.purchaseTicket(999L)
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 999 was not found",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository).markAsSoldIfPending(999L);
        verify(ticketRepository).findById(999L);

        verify(orderService, never())
                .createOrder(any(), any());
    }

    @Test
    void purchaseTicket_WhenPending_PassesCorrectTicketAndEmailToOrderService() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(2L);
        ticket.setStatus("PENDING");
        ticket.setCustomerEmail("customer2@example.com");

        Order order = new Order();
        order.setTicket(ticket);
        order.setCustomerEmail("customer2@example.com");
        order.setStatus("CREATED");

        when(ticketRepository.markAsSoldIfPending(2L))
                .thenReturn(1);

        when(ticketRepository.findById(2L))
                .thenReturn(Optional.of(ticket));

        when(orderService.createOrder(ticket, "customer2@example.com"))
                .thenReturn(order);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        // Act
        Order result = ticketService.purchaseTicket(2L);

        // Assert
        assertSame(order, result);

        verify(orderService).createOrder(
                ticket,
                "customer2@example.com"
        );
    }

    @Test
    void purchaseTicket_WhenTicketCannotBeFound_DoesNotCreateOrder() {

        // Arrange
        when(ticketRepository.markAsSoldIfPending(999L))
                .thenReturn(1);

        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        // Act & Assert
        assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.purchaseTicket(999L)
        );

        // Verify
        verify(ticketRepository).markAsSoldIfPending(999L);
        verify(ticketRepository).findById(999L);

        verify(orderService, never())
                .createOrder(any(), any());
    }

    @Test
    void testConcurrentPurchase_WhenPending_SellsTicket() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act
        TicketResponse response =
                ticketService.testConcurrentPurchase(1L);

        // Assert
        assertEquals("SOLD", response.getStatus());

        verify(ticketRepository).findById(1L);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void testConcurrentPurchase_WhenSold_ThrowsTicketConflictException() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("SOLD");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        TicketConflictException exception = assertThrows(
                TicketConflictException.class,
                () -> ticketService.testConcurrentPurchase(1L)
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 1 is already SOLD",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository).findById(1L);

        verify(ticketRepository, never())
                .save(ticket);
    }

    @Test
    void testConcurrentPurchase_WhenTicketDoesNotExist_ThrowsTicketNotFoundException() {

        // Arrange
        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        TicketNotFoundException exception = assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.testConcurrentPurchase(999L)
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 999 was not found",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository).findById(999L);

        verify(ticketRepository, never())
                .save(any());
    }

    @Test
    void testPessimisticPurchase_WhenPending_SellsTicket() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act
        TicketResponse response =
                ticketService.testPessimisticPurchase(1L);

        // Assert
        assertEquals("SOLD", response.getStatus());

        verify(ticketRepository).findByIdForUpdate(1L);
        verify(ticketRepository).save(ticket);
    }

    @Test
    void testPessimisticPurchase_WhenSold_ThrowsTicketConflictException() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("SOLD");

        when(ticketRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(ticket));

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        TicketConflictException exception = assertThrows(
                TicketConflictException.class,
                () -> ticketService.testPessimisticPurchase(1L)
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 1 is already SOLD",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository).findByIdForUpdate(1L);

        verify(ticketRepository, never())
                .save(ticket);
    }

    @Test
    void testPessimisticPurchase_WhenTicketDoesNotExist_ThrowsTicketNotFoundException() {

        // Arrange
        when(ticketRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        TicketNotFoundException exception = assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.testPessimisticPurchase(999L)
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 999 was not found",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository).findByIdForUpdate(999L);

        verify(ticketRepository, never())
                .save(any());
    }

    @Test
    void purchaseTicketAtomically_WhenPending_SellsTicket() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.empty());

        when(ticketRepository.markAsSoldIfPending(1L))
                .thenAnswer(invocation -> {
                    ticket.setStatus("SOLD");
                    return 1;
                });

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act
        TicketResponse response =
                ticketService.purchaseTicketAtomically(1L, "key-123");

        // Assert
        assertEquals("SOLD", response.getStatus());
        assertEquals("key-123", ticket.getIdempotencyKey());

        // Verify
        verify(ticketRepository)
                .findByIdempotencyKey("key-123");

        verify(ticketRepository)
                .markAsSoldIfPending(1L);

        verify(ticketRepository)
                .findById(1L);

        verify(ticketRepository)
                .save(ticket);
    }

    @Test
    void purchaseTicketAtomically_WhenIdempotencyKeyAlreadyExists_ReturnsExistingTicket() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("SOLD");
        ticket.setIdempotencyKey("key-123");

        when(ticketRepository.findByIdempotencyKey("key-123"))
                .thenReturn(Optional.of(ticket));

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act
        TicketResponse response =
                ticketService.purchaseTicketAtomically(1L, "key-123");

        // Assert
        assertEquals(1L, response.getId());
        assertEquals("SOLD", response.getStatus());

        // Verify
        verify(ticketRepository)
                .findByIdempotencyKey("key-123");

        verify(ticketRepository, never())
                .markAsSoldIfPending(anyLong());

        verify(ticketRepository, never())
                .findById(anyLong());

        verify(ticketRepository, never())
                .save(any());
    }

    @Test
    void purchaseTicketAtomically_WhenTicketIsNoLongerAvailable_ThrowsTicketConflictException() {

        // Arrange
        when(ticketRepository.findByIdempotencyKey("key-456"))
                .thenReturn(Optional.empty());

        when(ticketRepository.markAsSoldIfPending(1L))
                .thenReturn(0);

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        TicketConflictException exception = assertThrows(
                TicketConflictException.class,
                () -> ticketService.purchaseTicketAtomically(1L, "key-456")
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 1 is no longer available for purchase",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository)
                .findByIdempotencyKey("key-456");

        verify(ticketRepository)
                .markAsSoldIfPending(1L);

        verify(ticketRepository, never())
                .findById(1L);

        verify(ticketRepository, never())
                .save(any());
    }

    @Test
    void purchaseTicketAtomically_WhenTicketCannotBeFoundAfterUpdate_ThrowsTicketNotFoundException() {

        // Arrange
        when(ticketRepository.findByIdempotencyKey("key-789"))
                .thenReturn(Optional.empty());

        when(ticketRepository.markAsSoldIfPending(999L))
                .thenReturn(1);

        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act & Assert
        TicketNotFoundException exception = assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.purchaseTicketAtomically(999L, "key-789")
        );

        // Assert exception message
        assertEquals(
                "Ticket with id 999 was not found",
                exception.getMessage()
        );

        // Verify
        verify(ticketRepository)
                .findByIdempotencyKey("key-789");

        verify(ticketRepository)
                .markAsSoldIfPending(999L);

        verify(ticketRepository)
                .findById(999L);

        verify(ticketRepository, never())
                .save(any());
    }

    @Test
    void getTicket_WhenTicketExists_ReturnsTicketResponse() {

        // Arrange
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setTicketNumber("TICKET-001");
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("25.00"));
        ticket.setEventName("Magic Kingdom");
        ticket.setCustomerEmail("customer@example.com");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        TicketService ticketService =
                new TicketService(
                        ticketRepository,
                        orderService
                );

        // Act
        TicketResponse response =
                ticketService.getTicket(1L);

        // Assert
        assertEquals(1L, response.getId());
        assertEquals("TICKET-001", response.getTicketNumber());
        assertEquals("PENDING", response.getStatus());
        assertEquals(
                new BigDecimal("25.00"),
                response.getPrice()
        );
        assertEquals(
                "Magic Kingdom",
                response.getEventName()
        );
        assertEquals(
                "customer@example.com",
                response.getCustomerEmail()
        );

        // Verify
        verify(ticketRepository).findById(1L);
    }

    @Test
    void getTicket_WhenTicketDoesNotExist_ThrowsTicketNotFoundException() {
        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketNotFoundException exception = assertThrows(
                TicketNotFoundException.class,
                () -> ticketService.getTicket(999L)
        );

        assertEquals(
                "Ticket with id 999 was not found",
                exception.getMessage()
        );

        verify(ticketRepository).findById(999L);
    }

    @Test
    void getTicket_WhenTicketExists_UsesCorrectRepositoryCall() {
        Ticket ticket = new Ticket();
        ticket.setId(5L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findById(5L))
                .thenReturn(Optional.of(ticket));

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getTicket(5L);

        verify(ticketRepository).findById(5L);
        verify(ticketRepository, times(1)).findById(5L);
        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void createTicket_WhenRequestIsValid_SavesAndReturnsTicket() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Magic Kingdom");
        request.setCustomerEmail("customer@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(10L);
        savedTicket.setTicketNumber("TICKET-010");
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("Magic Kingdom");
        savedTicket.setCustomerEmail("customer@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse response =
                ticketService.createTicket(request);

        assertEquals(10L, response.getId());
        assertEquals("TICKET-010", response.getTicketNumber());
        assertEquals("PENDING", response.getStatus());
        assertEquals(new BigDecimal("20"), response.getPrice());
        assertEquals("Magic Kingdom", response.getEventName());
        assertEquals("customer@example.com", response.getCustomerEmail());

        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void createTicket_WhenRequestIsValid_PassesCorrectTicketToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("EPCOT");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(20L);
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("EPCOT");
        savedTicket.setCustomerEmail("guest@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals("EPCOT", ticketPassedToRepository.getEventName());
        assertEquals(
                "guest@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );
        assertEquals(
                new BigDecimal("20"),
                ticketPassedToRepository.getPrice()
        );
    }

    @Test
    void createTicket_WhenSaved_ReturnsRepositorySavedTicket() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Hollywood Studios");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(25L);
        savedTicket.setTicketNumber("TICKET-025");
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("Hollywood Studios");
        savedTicket.setCustomerEmail("guest@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse response =
                ticketService.createTicket(request);

        assertEquals(25L, response.getId());
        assertEquals(
                "TICKET-025",
                response.getTicketNumber()
        );
    }

    @Test
    void createTicket_WhenRequestIsValid_SavesTicketExactlyOnce() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Animal Kingdom");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(26L);
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("Animal Kingdom");
        savedTicket.setCustomerEmail("guest@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        verify(ticketRepository, times(1))
                .save(any(Ticket.class));

        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void createTicket_WhenRequestIsValid_DoesNotCreateOrder() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Magic Kingdom");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(27L);
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("Magic Kingdom");
        savedTicket.setCustomerEmail("guest@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        verify(orderService, never())
                .createOrder(any(Ticket.class), anyString());
    }

    @Test
    void createTicket_WhenRequestIsValid_SetsDefaultPrice() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Hollywood Studios");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(28L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                BigDecimal.valueOf(20),
                ticketPassedToRepository.getPrice()
        );
    }

    @Test
    void createTicket_WhenRequestIsValid_SetsEventName() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("EPCOT");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(29L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "EPCOT",
                ticketPassedToRepository.getEventName()
        );
    }

    @Test
    void createTicket_WhenRequestIsValid_SetsCustomerEmail() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("EPCOT");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(30L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "guest@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );
    }

    @Test
    void createTicket_WhenRequestIsValid_MapsRequestFieldsIndependently() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Animal Kingdom");
        request.setCustomerEmail("different@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(31L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "Animal Kingdom",
                ticketPassedToRepository.getEventName()
        );

        assertEquals(
                "different@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );
    }

    @Test
    void createTicket_WhenRequestIsValid_DoesNotModifyRequest() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Magic Kingdom");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(32L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        assertEquals(
                "Magic Kingdom",
                request.getEventName()
        );

        assertEquals(
                "guest@example.com",
                request.getCustomerEmail()
        );

        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void createTicket_WhenRepositoryReturnsDifferentEntity_UsesSavedEntity() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("EPCOT");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(33L);
        savedTicket.setTicketNumber("TICKET-033");
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("EPCOT");
        savedTicket.setCustomerEmail("guest@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse response =
                ticketService.createTicket(request);

        assertEquals(33L, response.getId());
        assertEquals(
                "TICKET-033",
                response.getTicketNumber()
        );

        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void createTicket_WhenRepositorySaveFails_PropagatesException() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Magic Kingdom");
        request.setCustomerEmail("guest@example.com");

        RuntimeException databaseException =
                new RuntimeException("Database unavailable");

        when(ticketRepository.save(any(Ticket.class)))
                .thenThrow(databaseException);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.createTicket(request)
        );

        assertEquals(
                "Database unavailable",
                exception.getMessage()
        );

        verify(ticketRepository).save(any(Ticket.class));

        verifyNoInteractions(orderService);
    }

    @Test
    void createTicket_WhenRequestIsValid_SavesOnlyOnce() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Hollywood Studios");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(35L);
        savedTicket.setStatus("PENDING");
        savedTicket.setPrice(new BigDecimal("20"));
        savedTicket.setEventName("Hollywood Studios");
        savedTicket.setCustomerEmail("guest@example.com");

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        verify(ticketRepository, times(1))
                .save(any(Ticket.class));

        verify(ticketRepository, never())
                .findById(anyLong());

        verify(ticketRepository, never())
                .findAll();
    }

    @Test
    void createTicket_WhenRequestIsValid_PassesCompleteTicketToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Animal Kingdom");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(36L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "Animal Kingdom",
                ticketPassedToRepository.getEventName()
        );

        assertEquals(
                "guest@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );

        assertEquals(
                BigDecimal.valueOf(20),
                ticketPassedToRepository.getPrice()
        );
    }

    @Test
    void createTicket_WhenDifferentValuesProvided_UsesThoseValues() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Disney Springs");
        request.setCustomerEmail("another.customer@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(37L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "Disney Springs",
                ticketPassedToRepository.getEventName()
        );

        assertEquals(
                "another.customer@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );

        assertEquals(
                BigDecimal.valueOf(20),
                ticketPassedToRepository.getPrice()
        );
    }

    @Test
    void createTicket_WhenEventNameIsBlank_PassesBlankEventNameToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("");
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(38L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "",
                ticketPassedToRepository.getEventName()
        );

        assertEquals(
                "guest@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );
    }

    @Test
    void createTicket_WhenCustomerEmailIsBlank_PassesBlankEmailToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Magic Kingdom");
        request.setCustomerEmail("");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(39L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "Magic Kingdom",
                ticketPassedToRepository.getEventName()
        );

        assertEquals(
                "",
                ticketPassedToRepository.getCustomerEmail()
        );
    }

    @Test
    void createTicket_WhenEventNameIsNull_PassesNullEventNameToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName(null);
        request.setCustomerEmail("guest@example.com");

        Ticket savedTicket = new Ticket();
        savedTicket.setId(40L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertNull(
                ticketPassedToRepository.getEventName()
        );

        assertEquals(
                "guest@example.com",
                ticketPassedToRepository.getCustomerEmail()
        );
    }

    @Test
    void createTicket_WhenCustomerEmailIsNull_PassesNullEmailToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName("Magic Kingdom");
        request.setCustomerEmail(null);

        Ticket savedTicket = new Ticket();
        savedTicket.setId(41L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                "Magic Kingdom",
                ticketPassedToRepository.getEventName()
        );

        assertNull(
                ticketPassedToRepository.getCustomerEmail()
        );
    }

    @Test
    void createTicket_WhenRequestFieldsAreNull_PassesNullValuesToRepository() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName(null);
        request.setCustomerEmail(null);

        Ticket savedTicket = new Ticket();
        savedTicket.setId(42L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertNull(
                ticketPassedToRepository.getEventName()
        );

        assertNull(
                ticketPassedToRepository.getCustomerEmail()
        );

        assertEquals(
                BigDecimal.valueOf(20),
                ticketPassedToRepository.getPrice()
        );
    }

    @Test
    void createTicket_WhenRequestFieldsAreNull_SetsDefaultPrice() {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setEventName(null);
        request.setCustomerEmail(null);

        Ticket savedTicket = new Ticket();
        savedTicket.setId(43L);

        when(ticketRepository.save(any(Ticket.class)))
                .thenReturn(savedTicket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.createTicket(request);

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository).save(ticketCaptor.capture());

        Ticket ticketPassedToRepository =
                ticketCaptor.getValue();

        assertEquals(
                BigDecimal.valueOf(20),
                ticketPassedToRepository.getPrice()
        );
    }

    @Test
    void getAllTickets_WhenTicketsExist_ReturnsAllTickets() {
        Ticket ticket1 = new Ticket();
        ticket1.setId(1L);
        ticket1.setStatus("PENDING");

        Ticket ticket2 = new Ticket();
        ticket2.setId(2L);
        ticket2.setStatus("SOLD");

        List<Ticket> tickets =
                List.of(ticket1, ticket2);

        when(ticketRepository.findAll())
                .thenReturn(tickets);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.getAllTickets();

        assertEquals(2, result.size());
        assertEquals(ticket1, result.get(0));
        assertEquals(ticket2, result.get(1));

        verify(ticketRepository).findAll();
    }

    @Test
    void getAllTickets_WhenNoTicketsExist_ReturnsEmptyList() {
        when(ticketRepository.findAll())
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.getAllTickets();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(ticketRepository).findAll();
    }

    @Test
    void getAllTickets_WhenTicketsExist_ReturnsRepositoryResult() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        List<Ticket> tickets =
                List.of(ticket);

        when(ticketRepository.findAll())
                .thenReturn(tickets);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.getAllTickets();

        assertSame(tickets, result);

        verify(ticketRepository).findAll();
    }

    @Test
    void getAllTickets_DoesNotSaveTickets() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findAll())
                .thenReturn(List.of(ticket));

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getAllTickets();

        verify(ticketRepository).findAll();

        verify(ticketRepository, never())
                .save(any(Ticket.class));
    }

    @Test
    void getAllTickets_DoesNotCreateOrder() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findAll())
                .thenReturn(List.of(ticket));

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getAllTickets();

        verify(orderService, never())
                .createOrder(any(Ticket.class), anyString());
    }

    @Test
    void getAllTickets_CallsRepositoryExactlyOnce() {
        when(ticketRepository.findAll())
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getAllTickets();

        verify(ticketRepository, times(1))
                .findAll();

        verifyNoMoreInteractions(ticketRepository);
    }

//    @Test
//    void getTicketsByStatus_WhenTicketsExist_ReturnsTickets() {
//        Ticket ticket1 = new Ticket();
//        ticket1.setId(1L);
//        ticket1.setStatus("PENDING");
//
//        Ticket ticket2 = new Ticket();
//        ticket2.setId(2L);
//        ticket2.setStatus("PENDING");
//
//        List<Ticket> tickets = List.of(ticket1, ticket2);
//
//        when(ticketRepository.findByStatus("PENDING"))
//                .thenReturn(tickets);
//
//        TicketService ticketService =
//                new TicketService(ticketRepository, orderService);
//
//        List<Ticket> result =
//                ticketService.getTicketsByStatus("PENDING");
//
//        assertEquals(tickets, result);
//    }

//    @Test
//    void getTicketsByStatus_WhenNoTicketsExist_ReturnsEmptyList() {
//        when(ticketRepository.findByStatus("CANCELLED"))
//                .thenReturn(List.of());
//
//        TicketService ticketService =
//                new TicketService(ticketRepository, orderService);
//
//        List<Ticket> result =
//                ticketService.getTicketsByStatus("CANCELLED");
//
//        assertTrue(result.isEmpty());
//    }

//    @Test
//    void getTicketsByStatus_CallsRepositoryExactlyOnce() {
//        when(ticketRepository.findByStatus("PENDING"))
//                .thenReturn(List.of());
//
//        TicketService ticketService =
//                new TicketService(ticketRepository, orderService);
//
//        ticketService.getTicketsByStatus("PENDING");
//
//        verify(ticketRepository, times(1))
//                .findByStatus("PENDING");
//
//        verifyNoMoreInteractions(ticketRepository);
//    }

    @Test
    void getTicketsByEventNameNative_WhenTicketsExist_ReturnsTickets() {
        Ticket ticket1 = new Ticket();
        ticket1.setId(1L);
        ticket1.setEventName("Magic Kingdom");

        Ticket ticket2 = new Ticket();
        ticket2.setId(2L);
        ticket2.setEventName("Magic Kingdom");

        List<Ticket> tickets = List.of(ticket1, ticket2);

        when(ticketRepository.findByEventNameNative("Magic Kingdom"))
                .thenReturn(tickets);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.getTicketsByEventNameNative("Magic Kingdom");

        assertEquals(tickets, result);
    }

    @Test
    void getTicketsByEventNameNative_WhenNoTicketsExist_ReturnsEmptyList() {
        when(ticketRepository.findByEventNameNative("EPCOT"))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.getTicketsByEventNameNative("EPCOT");

        assertTrue(result.isEmpty());
    }

    @Test
    void getTicketsByEventNameNative_PassesCorrectEventNameToRepository() {
        when(ticketRepository.findByEventNameNative("Hollywood Studios"))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getTicketsByEventNameNative("Hollywood Studios");

        verify(ticketRepository)
                .findByEventNameNative("Hollywood Studios");
    }

    @Test
    void getTicketsByEventNameNative_CallsRepositoryExactlyOnce() {
        when(ticketRepository.findByEventNameNative("Magic Kingdom"))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getTicketsByEventNameNative("Magic Kingdom");

        verify(ticketRepository, times(1))
                .findByEventNameNative("Magic Kingdom");

        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void getTicketsByStatus_PassesCorrectStatusAndPageableToRepository() {
        Pageable pageable = PageRequest.of(1, 20);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findByStatus("SOLD", pageable))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getTicketsByStatus("SOLD", pageable);

        verify(ticketRepository)
                .findByStatus("SOLD", pageable);
    }

    @Test
    void getTicketsByStatus_Paginated_CallsRepositoryExactlyOnce() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findByStatus("PENDING", pageable))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.getTicketsByStatus("PENDING", pageable);

        verify(ticketRepository, times(1))
                .findByStatus("PENDING", pageable);

        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void searchTickets_WhenStatusProvided_ReturnsMatchingTickets() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");

        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(ticket));

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.searchTickets("PENDING");

        assertEquals(1, result.size());
        assertEquals(ticket, result.get(0));

        verify(ticketRepository)
                .findAll(any(Specification.class));
    }

    @Test
    void searchTickets_WhenNoTicketsMatch_ReturnsEmptyList() {
        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.searchTickets("CANCELLED");

        assertTrue(result.isEmpty());
    }

    @Test
    void searchTickets_CallsRepositoryExactlyOnce() {
        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.searchTickets("PENDING");

        verify(ticketRepository, times(1))
                .findAll(any(Specification.class));

        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void searchCustomSpecification_WhenMatchesExist_ReturnsTickets() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setEventName("Magic Kingdom");
        ticket.setPrice(new BigDecimal("150"));

        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(ticket));

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.searchCustomSpecification();

        assertEquals(1, result.size());
        assertEquals(ticket, result.get(0));

        verify(ticketRepository)
                .findAll(any(Specification.class));
    }

    @Test
    void searchCustomSpecification_WhenNoMatchesExist_ReturnsEmptyList() {
        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        List<Ticket> result =
                ticketService.searchCustomSpecification();

        assertTrue(result.isEmpty());
    }

    @Test
    void searchCustomSpecification_CallsRepositoryExactlyOnce() {
        when(ticketRepository.findAll(any(Specification.class)))
                .thenReturn(List.of());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.searchCustomSpecification();

        verify(ticketRepository, times(1))
                .findAll(any(Specification.class));

        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void searchTickets_WithCriteriaAndPageable_ReturnsPage() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setEventName("Magic Kingdom");

        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");
        criteria.setEventName("Magic Kingdom");

        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(ticket),
                        pageable,
                        1
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        Page<Ticket> result =
                ticketService.searchTickets(criteria, pageable);

        assertEquals(page, result);
    }

    @Test
    void searchTickets_WhenNoMatchesExist_ReturnsEmptyPage() {
        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("CANCELLED");

        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> emptyPage =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(emptyPage);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        Page<Ticket> result =
                ticketService.searchTickets(criteria, pageable);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void searchTickets_PassesCorrectPageableToRepository() {
        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");

        Pageable pageable = PageRequest.of(1, 20);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.searchTickets(criteria, pageable);

        verify(ticketRepository)
                .findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchTickets_WithCriteriaAndPageable_CallsRepositoryExactlyOnce() {
        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");

        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.searchTickets(criteria, pageable);

        verify(ticketRepository, times(1))
                .findAll(any(Specification.class), eq(pageable));

        verifyNoMoreInteractions(ticketRepository);
    }

    @Test
    void searchTicketsDto_WhenTicketsExist_ReturnsMappedDtos() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setTicketNumber("T-100");
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("150"));
        ticket.setEventName("Magic Kingdom");
        ticket.setCustomerEmail("customer@example.com");

        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");

        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(ticket),
                        pageable,
                        1
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        Page<TicketDto> result =
                ticketService.searchTicketsDto(criteria, pageable);

        assertEquals(1, result.getTotalElements());

        TicketDto dto = result.getContent().get(0);

        assertEquals(1L, dto.getId());
        assertEquals("T-100", dto.getTicketNumber());
        assertEquals("PENDING", dto.getStatus());
        assertEquals(new BigDecimal("150"), dto.getPrice());
        assertEquals("Magic Kingdom", dto.getEventName());
        assertEquals("customer@example.com", dto.getCustomerEmail());
    }

    @Test
    void searchTicketsDto_WhenNoTicketsMatch_ReturnsEmptyPage() {
        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");

        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> emptyPage =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(emptyPage);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        Page<TicketDto> result =
                ticketService.searchTicketsDto(criteria, pageable);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());

        verify(ticketRepository)
                .findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchTicketsDto_PreservesPageableInformation() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setTicketNumber("T-100");
        ticket.setStatus("PENDING");

        TicketSearchCriteria criteria = new TicketSearchCriteria();

        Pageable pageable = PageRequest.of(2, 5);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(ticket),
                        pageable,
                        11
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        Page<TicketDto> result =
                ticketService.searchTicketsDto(criteria, pageable);

        assertEquals(2, result.getNumber());
        assertEquals(5, result.getSize());
        assertEquals(11, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    void searchTicketsDto_PassesCorrectPageableToRepository() {
        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");

        Pageable pageable = PageRequest.of(1, 20);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.searchTicketsDto(criteria, pageable);

        verify(ticketRepository)
                .findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchTicketsDto_CallsRepositoryExactlyOnce() {
        TicketSearchCriteria criteria = new TicketSearchCriteria();
        criteria.setStatus("PENDING");

        Pageable pageable = PageRequest.of(0, 10);

        Page<Ticket> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(ticketRepository.findAll(
                any(Specification.class),
                eq(pageable)))
                .thenReturn(page);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.searchTicketsDto(criteria, pageable);

        verify(ticketRepository, times(1))
                .findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getAdminInformation_ReturnsExpectedInformation() {
        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        String result = ticketService.getAdminInformation();

        assertEquals(
                "Sensitive ticket administration information",
                result
        );
    }

    @Test
    void getSupportInformation_ReturnsCorrectTicketInformation() {
        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        String result =
                ticketService.getSupportInformation(123L);

        assertEquals(
                "Support information for ticket 123",
                result
        );
    }

    @Test
    void isTicketOwner_WhenEmailMatches_ReturnsTrue() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setCustomerEmail("customer@example.com");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("email", "customer@example.com")
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        boolean result =
                ticketService.isTicketOwner(1L, authentication);

        assertTrue(result);

        verify(ticketRepository)
                .findById(1L);
    }

    @Test
    void isTicketOwner_WhenEmailDoesNotMatch_ReturnsFalse() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setCustomerEmail("customer@example.com");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("email", "different@example.com")
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        boolean result =
                ticketService.isTicketOwner(1L, authentication);

        assertFalse(result);

        verify(ticketRepository)
                .findById(1L);
    }

    @Test
    void isTicketOwner_WhenEmailDiffersOnlyByCase_ReturnsTrue() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setCustomerEmail("Customer@Example.com");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("email", "customer@example.com")
        );

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        boolean result =
                ticketService.isTicketOwner(1L, authentication);

        assertTrue(result);
    }

    @Test
    void isTicketOwner_WhenTicketDoesNotExist_ThrowsRuntimeException() {
        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.isTicketOwner(999L, null)
        );

        assertEquals("Ticket not found", exception.getMessage());

        verify(ticketRepository)
                .findById(999L);
    }

    @Test
    void isTicketOwner_WhenAuthenticationIsNotJwt_ReturnsFalse() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setCustomerEmail("customer@example.com");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        Authentication authentication = mock(Authentication.class);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        boolean result =
                ticketService.isTicketOwner(1L, authentication);

        assertFalse(result);

        verify(ticketRepository)
                .findById(1L);
    }

    @Test
    void updateTicket_WhenTicketExists_UpdatesStatusAndPrice() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse result =
                ticketService.updateTicket(
                        1L,
                        "SOLD",
                        new BigDecimal("150")
                );

        assertEquals("SOLD", result.getStatus());
        assertEquals(new BigDecimal("150"), result.getPrice());

        verify(ticketRepository)
                .findById(1L);

        verify(ticketRepository)
                .save(ticket);
    }

    @Test
    void updateTicket_WhenTicketDoesNotExist_ThrowsRuntimeException() {
        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.updateTicket(
                        999L,
                        "SOLD",
                        new BigDecimal("150")
                )
        );

        assertEquals("Ticket not found", exception.getMessage());

        verify(ticketRepository)
                .findById(999L);

        verify(ticketRepository, never())
                .save(any(Ticket.class));
    }

    @Test
    void updateTicket_PassesCorrectTicketIdToRepository() {
        Ticket ticket = new Ticket();
        ticket.setId(25L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(25L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                25L,
                "SOLD",
                new BigDecimal("175")
        );

        verify(ticketRepository)
                .findById(25L);
    }

    //argThat() lets Mockito verify a property of the object passed to a mocked method.
    @Test
    void updateTicket_PassesUpdatedTicketToRepository() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                1L,
                "SOLD",
                new BigDecimal("175")
        );

        verify(ticketRepository)
                .save(argThat(savedTicket ->
                        "SOLD".equals(savedTicket.getStatus())
                                && new BigDecimal("175")
                                .compareTo(savedTicket.getPrice()) == 0
                ));
    }

    @Test
    void updateTicket_SavesTicketExactlyOnce() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                1L,
                "SOLD",
                new BigDecimal("175")
        );

        verify(ticketRepository, times(1))
                .save(ticket);
    }

    @Test
    void updateTicket_DoesNotCreateOrder() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                1L,
                "SOLD",
                new BigDecimal("175")
        );

        verifyNoInteractions(orderService);
    }

    @Test
    void updateTicket_WhenTicketDoesNotExist_DoesNotSaveOrCreateOrder() {
        when(ticketRepository.findById(999L))
                .thenReturn(Optional.empty());

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        assertThrows(
                RuntimeException.class,
                () -> ticketService.updateTicket(
                        999L,
                        "SOLD",
                        new BigDecimal("175")
                )
        );

        verify(ticketRepository)
                .findById(999L);

        verify(ticketRepository, never())
                .save(any(Ticket.class));

        verifyNoInteractions(orderService);
    }

    @Test
    void updateTicket_WhenStatusIsNull_SavesNullStatus() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse result =
                ticketService.updateTicket(
                        1L,
                        null,
                        new BigDecimal("175")
                );

        assertNull(result.getStatus());
        assertEquals(new BigDecimal("175"), result.getPrice());

        verify(ticketRepository)
                .save(ticket);
    }

    @Test
    void updateTicket_WhenPriceIsNull_SavesNullPrice() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse result =
                ticketService.updateTicket(
                        1L,
                        "SOLD",
                        null
                );

        assertEquals("SOLD", result.getStatus());
        assertNull(result.getPrice());

        verify(ticketRepository)
                .save(ticket);
    }

    @Test
    void updateTicket_WhenStatusAndPriceAreNull_SavesBothAsNull() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse result =
                ticketService.updateTicket(
                        1L,
                        null,
                        null
                );

        assertNull(result.getStatus());
        assertNull(result.getPrice());

        verify(ticketRepository)
                .save(ticket);
    }

    @Test
    void updateTicket_DoesNotModifyTicketId() {
        Ticket ticket = new Ticket();
        ticket.setId(42L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(42L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        TicketResponse resultResponse =
                ticketService.updateTicket(
                        42L,
                        "SOLD",
                        new BigDecimal("175")
                );

        assertEquals(42L, resultResponse.getId());
        assertEquals("SOLD", resultResponse.getStatus());
        assertEquals(
                new BigDecimal("175"),
                resultResponse.getPrice()
        );
    }

    @Test
    void updateTicket_OnlyFindsAndSavesTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                1L,
                "SOLD",
                new BigDecimal("175")
        );

        verify(ticketRepository)
                .findById(1L);

        verify(ticketRepository)
                .save(ticket);

        verifyNoMoreInteractions(ticketRepository);
        verifyNoInteractions(orderService);
    }

    @Test
    void updateTicket_SavesTheTicketReturnedByRepository() {
        Ticket ticket = new Ticket();
        ticket.setId(10L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(10L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                10L,
                "SOLD",
                new BigDecimal("200")
        );

        verify(ticketRepository)
                .save(same(ticket));
    }

    @Test
    void updateTicket_PassesCorrectTicketStateToSave() {
        Ticket ticket = new Ticket();
        ticket.setId(7L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("50"));

        when(ticketRepository.findById(7L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                7L,
                "CANCELLED",
                new BigDecimal("75")
        );

        verify(ticketRepository).save(argThat(savedTicket ->
                savedTicket.getId().equals(7L)
                        && "CANCELLED".equals(savedTicket.getStatus())
                        && new BigDecimal("75")
                        .compareTo(savedTicket.getPrice()) == 0
        ));
    }

    @Test
    void updateTicket_DoesNotModifyInputValues() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        String status = "SOLD";
        BigDecimal price = new BigDecimal("175");

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        when(ticketRepository.save(ticket))
                .thenReturn(ticket);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        ticketService.updateTicket(
                1L,
                status,
                price
        );

        assertEquals("SOLD", status);
        assertEquals(
                new BigDecimal("175"),
                price
        );
    }

    @Test
    void updateTicket_WhenRepositorySaveFails_PropagatesException() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus("PENDING");
        ticket.setPrice(new BigDecimal("100"));

        when(ticketRepository.findById(1L))
                .thenReturn(Optional.of(ticket));

        RuntimeException expectedException =
                new RuntimeException("Database error");

        when(ticketRepository.save(ticket))
                .thenThrow(expectedException);

        TicketService ticketService =
                new TicketService(ticketRepository, orderService);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> ticketService.updateTicket(
                        1L,
                        "SOLD",
                        new BigDecimal("175")
                )
        );

        assertSame(expectedException, exception);

        verify(ticketRepository)
                .findById(1L);

        verify(ticketRepository)
                .save(ticket);

        verifyNoInteractions(orderService);
    }*/
}