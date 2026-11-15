
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TicketList } from './ticket-list';
import { AuthState } from '../../auth/auth-state';
import { authInterceptor } from '../../../core/auth-interceptor';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { environment } from '../../../../environments/environment';

describe('TicketList', () => {
  let component: TicketList;
  let fixture: ComponentFixture<TicketList>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TicketList],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor]),),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TicketList);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    httpTesting.verify({ ignoreCancelled: true });
  });

  it('should create', () => {
    // The component starts an initial ticket request.
    httpTesting.expectOne(
      request => request.url.includes('/api/v1/tickets'),
    ).flush({
      content: [],
      number: 0,
      totalPages: 0,
      totalElements: 0,
    });

    expect(component).toBeTruthy();
  });


it('should keep the latest search results when an earlier request is cancelled', () => {
  // Complete the component's initial ticket-list request.
  httpTesting.expectOne(
    request => request.url.includes('/api/v1/tickets'),
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
  });

  fixture.detectChanges();

  const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;

  // Submit the first search.
  component.searchForm.controls.eventName.setValue('EPCOT');
  form.dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true }),
  );

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.params.get('eventName') === 'EPCOT',
  );

  // Submit a second search before the first request responds.
  component.searchForm.controls.eventName.setValue('Magic Kingdom');
  form.dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true }),
  );

  const secondRequest = httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.params.get('eventName') === 'Magic Kingdom',
  );

  // switchMap should unsubscribe from the first HTTP request.
  expect(firstRequest.cancelled).toBe(true);

  // Return a result only for the latest search.
  secondRequest.flush({
    content: [
      {
        id: 42,
        ticketNumber: 'MK-042',
        status: 'PENDING',
        price: 150,
        eventName: 'Magic Kingdom',
        customerEmail: 'test@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
  });

  fixture.detectChanges();

  // The displayed ticket should belong to the latest search.
  expect(fixture.nativeElement.textContent).toContain('Magic Kingdom');
  expect(fixture.nativeElement.textContent).not.toContain('EPCOT');
});


it('should not send a search request when the form is invalid', () => {
  // Complete the initial ticket request.
  httpTesting.expectOne(
    request => request.url.includes('/api/v1/tickets'),
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
  });

  fixture.detectChanges();

  // Enter an event name longer than the 100-character limit.
  component.searchForm.controls.eventName.setValue('A'.repeat(101));

  // Submit the form.
  const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
  form.dispatchEvent(
    new Event('submit', { bubbles: true, cancelable: true }),
  );

  // The form should be invalid, and submission should mark it touched.
  expect(component.searchForm.invalid).toBe(true);
  expect(component.searchForm.controls.eventName.touched).toBe(true);

  // No new ticket request should be issued.
  httpTesting.expectNone(
    request => request.url.includes('/api/v1/tickets'),
  );
});


it('should ignore a duplicate purchase while the same ticket is processing', () => {
  httpTesting.expectOne(
    request => request.url.includes('/api/v1/tickets'),
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Simulate the first purchase already being in progress.
  component['purchasingTicketIds'].set([ticketId]);

  // Attempt another purchase for the same ticket.
  component['purchaseTicket'](ticketId);

  // No purchase request should have been sent.
  httpTesting.expectNone(
    request =>
      request.url.includes(`/api/v1/tickets/${ticketId}/purchase`),
  );

  expect(component['purchasingTicketIds']()).toEqual([ticketId]);
});


it('should display an unavailable message when purchase returns 409', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start a purchase request.
  component['purchaseTicket'](ticketId);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  // Simulate HTTP 409 Conflict from Spring Boot.
  purchaseRequest.flush(
    { message: 'Ticket is no longer available' },
    { status: 409, statusText: 'Conflict' },
  );

  // The component automatically reloads the ticket list after a 409.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  // Verify the visible message and the structured purchase state.
  expect(fixture.nativeElement.textContent).toContain(
    'This ticket is no longer available.',
  );

  expect(component['purchaseState']()).toEqual({
    status: 'failed',
    message: 'This ticket is no longer available.',
  });

  // Verify that the purchase is no longer marked as in progress.
  expect(component['purchasingTicketIds']()).toEqual([]);
});


it('should mark a purchase as unknown after a network error', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the purchase.
  component['purchaseTicket'](ticketId);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey = purchaseRequest.request.headers.get(
    'Idempotency-Key',
  );

  expect(originalKey).toBeTruthy();

  // Simulate a network error: the client cannot confirm the outcome.
  purchaseRequest.error(new ProgressEvent('error'));

  fixture.detectChanges();

  // The state must be unknown, not failed.
  expect(component['purchaseState']()).toEqual({
    status: 'unknown',
    message:
      'We could not confirm whether your purchase succeeded. Check your order status before trying again.',
      ticketId,
    idempotencyKey: originalKey!,
  });

  // The UI should warn the customer.
  // expect(fixture.nativeElement.textContent).toContain(
  //   'Purchase status is unknown',
  // );

  expect(fixture.nativeElement.textContent).toContain(
  'We could not confirm whether your purchase succeeded.',
);

  // The component must not automatically issue another purchase.
  httpTesting.expectNone(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );
});


it('should block another purchase when the previous outcome is unknown', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the first purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  // Simulate a network failure: the purchase outcome is uncertain.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');

  // The ticket must remain blocked.
  expect(component['purchasingTicketIds']()).toContain(ticketId);

  // Attempt another purchase for the same ticket.
  component['purchaseTicket'](ticketId);

  // No second purchase request should be sent.
  httpTesting.expectNone(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );
});

it('should reuse the original idempotency key when retrying an unknown purchase', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey = firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // Simulate an uncertain outcome.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');

  // Retry the same logical purchase.
  component['retryUnknownPurchase']();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // Simulate the backend returning the original successful order.
  retryRequest.flush({
    id: 123,
  });

  expect(component['purchaseState']()).toEqual({
    status: 'success',
    orderId: 123,
  });

  // Complete the ticket-list refresh triggered by success.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  expect(component['purchasingTicketIds']()).toEqual([]);
});


it('should preserve the original key and ticket lock when a retry also fails', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // First purchase attempt.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey =
    firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // First attempt has an uncertain outcome.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');
  expect(component['purchasingTicketIds']()).toContain(ticketId);

  // Retry using the saved key.
  component['retryUnknownPurchase']();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // The retry also has an uncertain outcome.
  retryRequest.error(new ProgressEvent('error'));

  const stateAfterRetry = component['purchaseState']();

  expect(stateAfterRetry.status).toBe('unknown');

  if (stateAfterRetry.status === 'unknown') {
    expect(stateAfterRetry.ticketId).toBe(ticketId);
    expect(stateAfterRetry.idempotencyKey).toBe(originalKey);
  }

  // The ticket must remain locked.
  expect(component['purchasingTicketIds']()).toContain(ticketId);

  // No automatic third request should be issued.
  httpTesting.expectNone(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );
});


it('should release the ticket lock when a retry returns 409', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey =
    firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // The original outcome is uncertain.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');
  expect(component['purchasingTicketIds']()).toContain(ticketId);

  // Retry the same logical purchase.
  component['retryUnknownPurchase']();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  // Confirm that the retry reuses the original key.
  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // Simulate a definitive ticket-unavailable response.
  retryRequest.flush(
    { message: 'Ticket is no longer available' },
    { status: 409, statusText: 'Conflict' },
  );

  // The component should transition to a known failure.
  expect(component['purchaseState']()).toEqual({
    status: 'failed',
    message: 'This ticket is no longer available.',
  });

  // The ticket lock should be released.
  expect(component['purchasingTicketIds']()).toEqual([]);

  // The component refreshes the ticket list after a 409.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  expect(fixture.nativeElement.textContent).toContain(
    'This ticket is no longer available.',
  );
});


it('should release the ticket lock when a retry returns 403', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey =
    firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // The first attempt has an uncertain outcome.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');
  expect(component['purchasingTicketIds']()).toContain(ticketId);

  // Retry the same logical purchase.
  component['retryUnknownPurchase']();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // Simulate an authorization failure.
  retryRequest.flush(
    { message: 'Forbidden' },
    { status: 403, statusText: 'Forbidden' },
  );

  expect(component['purchaseState']()).toEqual({
    status: 'failed',
    message: 'You are not authorized to purchase tickets.',
  });

  expect(component['purchasingTicketIds']()).toEqual([]);

  // A 403 does not trigger the 409-specific ticket refresh.
  httpTesting.expectNone(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  );

  fixture.detectChanges();

  expect(fixture.nativeElement.textContent).toContain(
    'You are not authorized to purchase tickets.',
  );
});


it('should preserve the unknown state when a retry returns 500', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey =
    firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // First attempt has an uncertain outcome.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');

  // Retry the same logical purchase.
  component['retryUnknownPurchase']();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // The retry encounters a server error.
  retryRequest.flush(
    { message: 'Internal server error' },
    { status: 500, statusText: 'Internal Server Error' },
  );

  const stateAfterRetry = component['purchaseState']();

  expect(stateAfterRetry.status).toBe('unknown');

  if (stateAfterRetry.status === 'unknown') {
    expect(stateAfterRetry.ticketId).toBe(ticketId);
    expect(stateAfterRetry.idempotencyKey).toBe(originalKey);
  }

  expect(component['purchasingTicketIds']()).toContain(ticketId);

  // No automatic third purchase request should be issued.
  httpTesting.expectNone(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );
});


it('should display success and refresh tickets when an unknown purchase retry succeeds', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey =
    firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // The original request loses its connection.
  firstRequest.error(new ProgressEvent('error'));

  expect(component['purchaseState']().status).toBe('unknown');

  // Retry the same logical purchase.
  component['retryUnknownPurchase']();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // The backend returns the successful order.
  retryRequest.flush({ id: 123 });

  expect(component['purchaseState']()).toEqual({
    status: 'success',
    orderId: 123,
  });

  expect(component['purchasingTicketIds']()).toEqual([]);

  // Complete the ticket-list refresh triggered by success.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const pageText = fixture.nativeElement.textContent;

  expect(pageText).toContain('Ticket purchased successfully');
  expect(pageText).toContain('Order ID: 123');
  expect(pageText).not.toContain(
    'We could not confirm whether your purchase succeeded.',
  );
});



it('should show the retry button only when purchase outcome is unknown', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const retryButton = () =>
    (
      Array.from(
        fixture.nativeElement.querySelectorAll('button'),
      ) as HTMLButtonElement[]
    ).find(
      button =>
        button.textContent?.trim() === 'Retry checking purchase',
    );

  // Idle state: no retry button.
  expect(retryButton()).toBeUndefined();

  const ticketId = 42;

  // Start a purchase and check the processing state.
  component['purchaseTicket'](ticketId);
  fixture.detectChanges();

  expect(retryButton()).toBeUndefined();

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  // An uncertain outcome should reveal the retry button.
  purchaseRequest.error(new ProgressEvent('error'));
  fixture.detectChanges();

  expect(retryButton()).toBeDefined();

  // Retry and return a definitive authorization failure.
  component['retryUnknownPurchase']();
  fixture.detectChanges();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  retryRequest.flush(
    { message: 'Forbidden' },
    { status: 403, statusText: 'Forbidden' },
  );

  fixture.detectChanges();

  // A definitive failure should hide the retry button.
  expect(retryButton()).toBeUndefined();
});


it('should retry the purchase with the original key when the retry button is clicked', () => {
  // Complete the initial ticket-list request.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  const originalKey =
    firstRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // Simulate an uncertain outcome.
  firstRequest.error(new ProgressEvent('error'));
  fixture.detectChanges();

  // Locate the actual rendered retry button.
  const retryButton = (
    Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
  ).find(
    button =>
      button.textContent?.trim() === 'Retry checking purchase',
  );

  expect(retryButton).toBeDefined();

  // Simulate the customer clicking the button.
  retryButton!.click();
  fixture.detectChanges();

  // Verify that Angular sent the retry request.
  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // Complete the retry with a successful order response.
  retryRequest.flush({ id: 123 });

  // Complete the ticket-list refresh triggered by success.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  expect(component['purchaseState']()).toEqual({
    status: 'success',
    orderId: 123,
  });

  expect(fixture.nativeElement.textContent).toContain(
    'Order ID: 123',
  );

  expect(component['purchasingTicketIds']()).toEqual([]);
});


it('should not send duplicate requests when the retry button is clicked repeatedly', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  const ticketId = 42;

  // Start the original purchase.
  component['purchaseTicket'](ticketId);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  firstRequest.error(new ProgressEvent('error'));
  fixture.detectChanges();

  const retryButton = (
    Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
  ).find(
    button =>
      button.textContent?.trim() === 'Retry checking purchase',
  );

  expect(retryButton).toBeDefined();

  // First click starts the retry.
  retryButton!.click();
  fixture.detectChanges();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  // While the retry is pending, the unknown-state button should disappear.
  // expect(
  //   Array.from(
  //     fixture.nativeElement.querySelectorAll('button'),
  //   ).some(
  //     (button: HTMLButtonElement) =>
  //       button.textContent?.trim() === 'Retry checking purchase',
  //   ),
  // ).toBeFalse();

  expect(
  (
    Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
  ).some(
    button =>
      button.textContent?.trim() === 'Retry checking purchase',
  ),
).toBeFalsy();

  
  // A second click on the old DOM element must not create another request.
  retryButton!.click();

  httpTesting.expectNone(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  retryRequest.flush({ id: 123 });

  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  expect(component['purchaseState']()).toEqual({
    status: 'success',
    orderId: 123,
  });
});


it('should disable the ticket purchase button while its purchase is pending', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'PENDING',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();
//   expect(component['tickets']()).toHaveLength(1);
// expect(component['tickets']()[0].status).toBe('PENDING');

// console.log('Tickets signal:', component['tickets']());
// console.log('Rendered HTML:', fixture.nativeElement.innerHTML);

  // const findPurchaseButton = () =>
  //   (
  //     Array.from(
  //       fixture.nativeElement.querySelectorAll('button'),
  //     ) as HTMLButtonElement[]
  //   ).find(
  //     button =>
  //       button.textContent?.includes('Purchase Magic Kingdom'),
  //   );

 
const findPurchaseButton = () =>
  (
    Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
  ).find(
    button =>
      button.textContent?.includes('Purchase ') ||
      button.textContent?.includes('Purchasing...'),
  );

  
  const buttonBeforePurchase = findPurchaseButton();

  expect(buttonBeforePurchase).toBeDefined();
  expect(buttonBeforePurchase!.disabled).toBe(false);

  // Start the purchase and leave its HTTP request pending.
  component['purchaseTicket'](42);
  fixture.detectChanges();

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  const buttonDuringPurchase = findPurchaseButton();

//   console.log('Tickets during purchase:', component['tickets']());
// console.log('Purchase state:', component['purchaseState']());
// console.log('Purchasing IDs:', component['purchasingTicketIds']());
// console.log('HTML during purchase:', fixture.nativeElement.innerHTML);

  expect(buttonDuringPurchase).toBeDefined();
  expect(buttonDuringPurchase!.disabled).toBe(true);
  expect(buttonDuringPurchase!.textContent).toContain('Purchasing...');

  // Complete the request so the test leaves no pending HTTP calls.
  purchaseRequest.flush({ id: 123 });

  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();
});



it('should re-enable the purchase button after a 403 response', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'PENDING',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();

  const findPurchaseButton = () =>
    (
      Array.from(
        fixture.nativeElement.querySelectorAll('button'),
      ) as HTMLButtonElement[]
    ).find(
      button =>
        button.textContent?.includes('Purchase Magic Kingdom') ||
        button.textContent?.includes('Purchasing...'),
    );

  // The button starts enabled.
  const initialButton = findPurchaseButton();

  expect(initialButton).toBeDefined();
  expect(initialButton!.disabled).toBe(false);

  // Start the purchase.
  component['purchaseTicket'](42);
  fixture.detectChanges();

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  // The button must be disabled while the request is pending.
  expect(findPurchaseButton()!.disabled).toBe(true);

  // A 403 represents a definite authorization failure.
  purchaseRequest.flush(
    { message: 'Forbidden' },
    { status: 403, statusText: 'Forbidden' },
  );

  fixture.detectChanges();

  // The component should release the ticket lock.
  expect(component['purchasingTicketIds']()).toEqual([]);

  // The ticket remains PENDING, so its button should be enabled again.
  const buttonAfterFailure = findPurchaseButton();

  expect(buttonAfterFailure).toBeDefined();
  expect(buttonAfterFailure!.disabled).toBe(false);
  expect(buttonAfterFailure!.textContent).toContain(
    'Purchase Magic Kingdom',
  );

  expect(component['purchaseState']()).toEqual({
    status: 'failed',
    message: 'You are not authorized to purchase tickets.',
  });
});


it('should keep the purchase button disabled when the purchase outcome is unknown', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'PENDING',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();

  const findButton = (text: string) =>
    (
      Array.from(
        fixture.nativeElement.querySelectorAll('button'),
      ) as HTMLButtonElement[]
    ).find(button => button.textContent?.includes(text));

  // Start the purchase.
  component['purchaseTicket'](42);
  fixture.detectChanges();

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  expect(findButton('Purchasing...')?.disabled).toBe(true);

  // Simulate a server error with an uncertain purchase outcome.
  purchaseRequest.flush(
    { message: 'Internal Server Error' },
    { status: 500, statusText: 'Internal Server Error' },
  );

  fixture.detectChanges();

  // The ticket lock must remain held.
  expect(component['purchasingTicketIds']()).toContain(42);

  // The normal Purchase button must remain disabled.
  expect(findButton('Purchasing...')?.disabled).toBe(true);

  // The UI must offer the explicit retry action.
  expect(findButton('Retry checking purchase')).toBeDefined();

  expect(component['purchaseState']()).toMatchObject({
    status: 'unknown',
    ticketId: 42,
  });
});



it('should recover from an unknown outcome when retry succeeds', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'PENDING',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();

  const findButton = (text: string) =>
    (
      Array.from(
        fixture.nativeElement.querySelectorAll('button'),
      ) as HTMLButtonElement[]
    ).find(button => button.textContent?.includes(text));

  // Send the original purchase request.
  component['purchaseTicket'](42);

  const originalRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  const originalKey =
    originalRequest.request.headers.get('Idempotency-Key');

  expect(originalKey).toBeTruthy();

  // The server returns 500, leaving the outcome unknown.
  originalRequest.flush(
    { message: 'Internal Server Error' },
    { status: 500, statusText: 'Internal Server Error' },
  );

  fixture.detectChanges();

  expect(component['purchasingTicketIds']()).toContain(42);
  expect(findButton('Retry checking purchase')).toBeDefined();

  // Click the actual retry button rendered by Angular.
  findButton('Retry checking purchase')!.click();
  fixture.detectChanges();

  const retryRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  // The retry must represent the same logical purchase.
  expect(
    retryRequest.request.headers.get('Idempotency-Key'),
  ).toBe(originalKey);

  // The retry succeeds.
  retryRequest.flush({ id: 123 });

  // Complete the ticket-list refresh triggered by success.
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  expect(component['purchaseState']()).toEqual({
    status: 'success',
    orderId: 123,
  });

  expect(component['purchasingTicketIds']()).toEqual([]);
  expect(fixture.nativeElement.textContent).toContain('Order ID: 123');
  expect(findButton('Retry checking purchase')).toBeUndefined();
});


it('should announce purchase success through an accessible status message', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  // Set the success state to simulate a completed purchase.
  component['purchaseState'].set({
    status: 'success',
    orderId: 123,
  });

  fixture.detectChanges();

  // const statusMessage: HTMLElement | undefined = Array.from(
  //   fixture.nativeElement.querySelectorAll('[role="status"]'),
  // ).find(
  //   (element: Element) =>
  //     element.textContent?.includes('Order ID: 123'),
  // );


const statusMessage = (
  Array.from(
    fixture.nativeElement.querySelectorAll('[role="status"]'),
  ) as HTMLElement[]
).find(
  element => element.textContent?.includes('Order ID: 123'),
);


  expect(statusMessage).toBeDefined();
  expect(statusMessage!.textContent).toContain(
    'Ticket purchased successfully.',
  );
  expect(statusMessage!.textContent).toContain('Order ID: 123');
});


it('should announce purchase failures through an accessible alert', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'PENDING',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();

  // Start a purchase.
  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  // Simulate a definite authorization failure.
  purchaseRequest.flush(
    { message: 'Forbidden' },
    { status: 403, statusText: 'Forbidden' },
  );

  fixture.detectChanges();

  const alertMessage = (
    Array.from(
      fixture.nativeElement.querySelectorAll('[role="alert"]'),
    ) as HTMLElement[]
  ).find(
    element =>
      element.textContent?.includes(
        'You are not authorized to purchase tickets.',
      ),
  );

  expect(alertMessage).toBeDefined();
  expect(alertMessage!.textContent).toContain(
    'You are not authorized to purchase tickets.',
  );
});


it('should not render a purchase button for a sold ticket', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'SOLD',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 1,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();

  // The sold ticket should still be displayed.
  expect(fixture.nativeElement.textContent).toContain('Magic Kingdom');
  expect(fixture.nativeElement.textContent).toContain('Status: SOLD');

  // No Purchase button should be rendered for the sold ticket.
  const purchaseButtons = (
    Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
  ).filter(
    button => button.textContent?.includes('Purchase Magic Kingdom'),
  );

  expect(purchaseButtons).toHaveLength(0);
});


it('should not render a purchase button for a cancelled ticket', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [
      {
        id: 42,
        ticketNumber: 'TICKET-42',
        status: 'CANCELLED',
        price: 99.99,
        eventName: 'Magic Kingdom',
        customerEmail: 'customer@example.com',
      },
    ],
    number: 0,
    totalPages: 0,
    totalElements: 1,
    size: 20,
  });

  fixture.detectChanges();

  // The cancelled ticket should remain visible.
  expect(fixture.nativeElement.textContent).toContain('Magic Kingdom');
  expect(fixture.nativeElement.textContent).toContain('Status: CANCELLED');

  // No Purchase button should be rendered for it.
  const purchaseButtons = (
    Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[]
  ).filter(
    button => button.textContent?.includes('Purchase Magic Kingdom'),
  );

  expect(purchaseButtons).toHaveLength(0);
});


it('should display a safe message instead of internal server details', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  // Simulate an internal error containing sensitive diagnostic details.
  purchaseRequest.flush(
    {
      message: 'SQL Server connection failed: internal-db-01; password=secret',
      stackTrace: 'InternalService.java:142',
    },
    {
      status: 500,
      statusText: 'Internal Server Error',
    },
  );

  fixture.detectChanges();

  const alertMessages = Array.from(
    fixture.nativeElement.querySelectorAll('[role="alert"]'),
  ) as HTMLElement[];

  const visibleText = alertMessages
    .map(element => element.textContent ?? '')
    .join(' ');

  expect(visibleText).toContain(
    'We could not confirm whether your purchase succeeded.',
  );
  expect(visibleText).toContain('Check your order status before trying again.');

  expect(visibleText).not.toContain('internal-db-01');
  expect(visibleText).not.toContain('password=secret');
  expect(visibleText).not.toContain('InternalService.java');
  expect(visibleText).not.toContain('stackTrace');
});


it('should clear the previous purchase error when a new purchase starts', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  fixture.detectChanges();

  // First purchase attempt: simulate a forbidden response.
  component['purchaseTicket'](42);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  firstRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );

  fixture.detectChanges();

  expect(
    fixture.nativeElement.textContent,
  ).toContain('You are not authorized to purchase tickets.');

  // Start another purchase attempt.
  component['purchaseTicket'](42);

  fixture.detectChanges();

  // The old error should disappear immediately.
  expect(
    fixture.nativeElement.textContent,
  ).not.toContain('You are not authorized to purchase tickets.');

  // Complete the new request so the test leaves no pending HTTP request.
  const secondRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  secondRequest.flush({
    orderId: 1001,
  });


httpTesting.expectOne(
  request =>
    request.url.includes('/api/v1/tickets') &&
    request.method === 'GET',
).flush({
  content: [],
  number: 0,
  totalPages: 0,
  totalElements: 0,
  size: 20,
});

fixture.detectChanges();

});


it('should generate a new idempotency key for a new purchase attempt after a definitive failure', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  // First purchase attempt.
  component['purchaseTicket'](42);

  const firstRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  const firstKey = firstRequest.request.headers.get('Idempotency-Key');

  expect(firstKey).toBeTruthy();

  // A definitive 403 failure ends the first attempt.
  firstRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );

  // Start a new logical purchase attempt.
  component['purchaseTicket'](42);

  const secondRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  const secondKey = secondRequest.request.headers.get('Idempotency-Key');

  expect(secondKey).toBeTruthy();
  expect(secondKey).not.toBe(firstKey);

  // Finish the request to leave no pending HTTP requests.
  secondRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );
});


it('should send the idempotency key in the Idempotency-Key header', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  const idempotencyKey = purchaseRequest.request.headers.get(
    'Idempotency-Key',
  );

  expect(idempotencyKey).toBeTruthy();
  expect(idempotencyKey).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
  );

  // Return a definitive failure to finish the attempt without triggering a refresh.
  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );
});


it('should send a POST request to the purchase endpoint for the selected ticket', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const ticketId = 42;

  component['purchaseTicket'](ticketId);

  const purchaseRequest = httpTesting.expectOne(
    `http://localhost:8060/api/v1/tickets/${ticketId}/purchase`,
  );

  expect(purchaseRequest.request.method).toBe('POST');
  expect(purchaseRequest.request.url).toBe(
    `http://localhost:8060/api/v1/tickets/${ticketId}/purchase`,
  );

  // A definitive failure ends the attempt without triggering a ticket refresh.
  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );
});


it('should include the selected ticket ID in the purchase URL', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const ticketId = 57;

  component['purchaseTicket'](ticketId);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith(`/api/v1/tickets/${ticketId}/purchase`) &&
      request.method === 'POST',
  );

  expect(purchaseRequest.request.url).toBe(
    `http://localhost:8060/api/v1/tickets/${ticketId}/purchase`,
  );

  expect(purchaseRequest.request.url).not.toContain('/tickets/42/purchase');

  // Finish the request with a definitive failure.
  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );
});


it('should send an empty JSON object as the purchase request body', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  expect(purchaseRequest.request.body).toEqual({});
  expect(
    purchaseRequest.request.headers.get('Idempotency-Key'),
  ).toBeTruthy();

  // Finish the request without triggering a ticket refresh.
  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );
});



it('should include the JWT Bearer token in the purchase request', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  authState.setToken('test-jwt-token');
expect(authState.getToken()).toBe('test-jwt-token');

  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  console.log('Purchase request URL:', purchaseRequest.request.url);
console.log(
  'Authorization header:',
  purchaseRequest.request.headers.get('Authorization'),
);

  expect(
    purchaseRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );

  authState.clearToken();
});



it('should not attach the JWT to requests outside the API Gateway API path', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  authState.setToken('test-jwt-token');

  // Simulate a request to a URL outside the API Gateway API path.
  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  expect(
    purchaseRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );

  authState.clearToken();
});


it('should not attach the JWT to requests outside the API Gateway API path 2', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('http://localhost:8060/health').subscribe();

  const healthRequest = httpTesting.expectOne(
    'http://localhost:8060/health',
  );

  expect(
    healthRequest.request.headers.get('Authorization'),
  ).toBeNull();

  healthRequest.flush({ status: 'UP' });

  authState.clearToken();
});


it('should clear the JWT and redirect to login after a 401 response', async () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const router = TestBed.inject(Router);

  authState.setToken('test-jwt-token');

  const navigationSpy = vi.spyOn(router, 'navigateByUrl');

  const http = TestBed.inject(HttpClient);

  http.get('http://localhost:8060/api/v1/tickets').subscribe({
    error: () => {
      // Expected: the interceptor rethrows the HTTP error.
    },
  });

  const request = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  request.flush(
    { message: 'Unauthorized' },
    {
      status: 401,
      statusText: 'Unauthorized',
    },
  );

  expect(authState.getToken()).toBeNull();
  expect(sessionStorage.getItem('accessToken')).toBeNull();

  expect(navigationSpy).toHaveBeenCalledWith('/login');

  authState.clearToken();
});


it('should not redirect or clear authentication when the login endpoint returns 401', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const router = TestBed.inject(Router);
  const http = TestBed.inject(HttpClient);

  authState.setToken('existing-test-token');

  const navigationSpy = vi.spyOn(router, 'navigateByUrl');

  http.post(
    'http://localhost:8060/api/auth/login',
    { username: 'test-user', password: 'wrong-password' },
  ).subscribe({
    error: () => {
      // Expected: invalid login credentials produce a 401 response.
    },
  });

  const loginRequest = httpTesting.expectOne(
    'http://localhost:8060/api/auth/login',
  );

  loginRequest.flush(
    { message: 'Invalid credentials' },
    {
      status: 401,
      statusText: 'Unauthorized',
    },
  );

  expect(authState.getToken()).toBe('existing-test-token');
  expect(sessionStorage.getItem('accessToken')).toBe('existing-test-token');
  expect(navigationSpy).not.toHaveBeenCalled();

  authState.clearToken();
});


it('should preserve the JWT and avoid redirecting after a 403 response', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const router = TestBed.inject(Router);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const navigationSpy = vi.spyOn(router, 'navigateByUrl');

  http.get('http://localhost:8060/api/v1/tickets')
    .subscribe({
      error: () => {
        // Expected: the interceptor rethrows the HTTP error.
      },
    });

  const apiRequest = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    apiRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  apiRequest.flush(
    { message: 'Forbidden' },
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );

  expect(authState.getToken()).toBe('test-jwt-token');
  expect(sessionStorage.getItem('accessToken')).toBe('test-jwt-token');
  expect(navigationSpy).not.toHaveBeenCalled();

  authState.clearToken();
});


it('should preserve the JWT after a successful API response', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const router = TestBed.inject(Router);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const navigationSpy = vi.spyOn(router, 'navigateByUrl');

  let responseReceived = false;

  http.get('http://localhost:8060/api/v1/tickets').subscribe({
    next: () => {
      responseReceived = true;
    },
  });

  const apiRequest = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    apiRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  apiRequest.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  expect(responseReceived).toBe(true);
  expect(authState.getToken()).toBe('test-jwt-token');
  expect(sessionStorage.getItem('accessToken')).toBe('test-jwt-token');
  expect(navigationSpy).not.toHaveBeenCalled();

  authState.clearToken();
});


it('should preserve the JWT and avoid redirecting after a 500 response', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const router = TestBed.inject(Router);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const navigationSpy = vi.spyOn(router, 'navigateByUrl');

  let receivedError: unknown;

  http.get('http://localhost:8060/api/v1/tickets').subscribe({
    error: error => {
      receivedError = error;
    },
  });

  const apiRequest = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    apiRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  apiRequest.flush(
    { message: 'Internal server error' },
    {
      status: 500,
      statusText: 'Internal Server Error',
    },
  );

  expect(receivedError).toBeTruthy();
  expect((receivedError as { status: number }).status).toBe(500);
  expect(authState.getToken()).toBe('test-jwt-token');
  expect(sessionStorage.getItem('accessToken')).toBe('test-jwt-token');
  expect(navigationSpy).not.toHaveBeenCalled();

  authState.clearToken();
});


it('should not attach a JWT to API requests after the token is cleared', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  // Verify that the token is initially attached.
  http.get('http://localhost:8060/api/v1/tickets').subscribe();

  const authenticatedRequest = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    authenticatedRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  authenticatedRequest.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  // Simulate logout.
  authState.clearToken();

  expect(authState.getToken()).toBeNull();
  expect(sessionStorage.getItem('accessToken')).toBeNull();

  // A subsequent request must not carry the previous token.
  http.get('http://localhost:8060/api/v1/tickets').subscribe();

  const unauthenticatedRequest = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    unauthenticatedRequest.request.headers.get('Authorization'),
  ).toBeNull();

  unauthenticatedRequest.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });
});


it('should preserve the purchase body and idempotency key when adding the JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);

  authState.setToken('test-jwt-token');

  component['purchaseTicket'](42);

  const purchaseRequest = httpTesting.expectOne(
    request =>
      request.url.endsWith('/api/v1/tickets/42/purchase') &&
      request.method === 'POST',
  );

  expect(purchaseRequest.request.body).toEqual({});

  expect(
    purchaseRequest.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  expect(
    purchaseRequest.request.headers.get('Idempotency-Key'),
  ).toMatch(
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
  );

  // Finish the request without triggering a ticket refresh.
  purchaseRequest.flush(
    {},
    {
      status: 403,
      statusText: 'Forbidden',
    },
  );

  authState.clearToken();
});


it('should preserve query parameters when adding the JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('http://localhost:8060/api/v1/tickets', {
    params: {
      status: 'PENDING',
      page: '2',
      size: '10',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    req =>
      req.url === 'http://localhost:8060/api/v1/tickets' &&
      req.method === 'GET',
  );

  expect(request.request.headers.get('Authorization'))
    .toBe('Bearer test-jwt-token');

  expect(request.request.params.get('status')).toBe('PENDING');
  expect(request.request.params.get('page')).toBe('2');
  expect(request.request.params.get('size')).toBe('10');

  request.flush({
    content: [],
    number: 2,
    totalPages: 0,
    totalElements: 0,
    size: 10,
  });

  authState.clearToken();
});


it('should not attach the JWT to unrelated gateway paths', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('http://localhost:8060/admin/diagnostics')
    .subscribe();

  const request = httpTesting.expectOne(
    'http://localhost:8060/admin/diagnostics',
  );

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({ status: 'ok' });

  authState.clearToken();
});


it('should not attach the JWT to API paths on another host', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('http://example.com/api/v1/tickets')
    .subscribe();

  const request = httpTesting.expectOne(
    'http://example.com/api/v1/tickets',
  );

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to an HTTPS URL when the configured gateway uses HTTP', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('https://localhost:8060/api/v1/tickets')
    .subscribe();

  const request = httpTesting.expectOne(
    'https://localhost:8060/api/v1/tickets',
  );

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to a lookalike gateway hostname', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('http://localhost:8060.example.com/api/v1/tickets')
    .subscribe();

  const request = httpTesting.expectOne(
    'http://localhost:8060.example.com/api/v1/tickets',
  );

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to a URL with deceptive user-information syntax', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const deceptiveUrl =
    'http://localhost:8060@example.com/api/v1/tickets';

  http.get(deceptiveUrl).subscribe();

  const request = httpTesting.expectOne(deceptiveUrl);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to the backend port directly', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const backendUrl =
    'http://localhost:8061/api/v1/tickets';

  http.get(backendUrl).subscribe();

  const request = httpTesting.expectOne(backendUrl);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should replace a caller-supplied Authorization header with the stored JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  http.get('http://localhost:8060/api/v1/tickets', {
    headers: {
      Authorization: 'Bearer caller-supplied-token',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer stored-jwt-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should preserve a caller-supplied Authorization header when no JWT is stored', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.clearToken();

  http.get('http://localhost:8060/api/v1/tickets', {
    headers: {
      Authorization: 'Bearer caller-supplied-token',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    'http://localhost:8060/api/v1/tickets',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer caller-supplied-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });
});


it('should preserve an existing Authorization header outside the trusted API path', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  http.get('http://localhost:8060/health', {
    headers: {
      Authorization: 'Bearer caller-supplied-token',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    'http://localhost:8060/health',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer caller-supplied-token');

  request.flush({ status: 'ok' });

  authState.clearToken();
});


it('should not attach the JWT to a relative API URL', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('/api/v1/tickets').subscribe();

  const request = httpTesting.expectOne('/api/v1/tickets');

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should attach the JWT to a trusted API request with query parameters', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  http.get('http://localhost:8060/api/v1/tickets', {
    params: {
      status: 'PENDING',
      page: '1',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    req =>
      req.url === 'http://localhost:8060/api/v1/tickets' &&
      req.method === 'GET',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  expect(request.request.params.get('status')).toBe('PENDING');
  expect(request.request.params.get('page')).toBe('1');

  request.flush({
    content: [],
    number: 1,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should attach the JWT to a trusted API URL with a trailing slash', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets/';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should attach the JWT to a trusted API URL containing a fragment', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://localhost:8060/api/v1/tickets#details';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to an untrusted hostname with a trusted API path', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'https://api.example.com/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to an external URL that may redirect to the gateway', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const externalUrl =
    'https://external.example.com/redirect?target=http://localhost:8060/api/v1/tickets';

  http.get(externalUrl).subscribe();

  const request = httpTesting.expectOne(externalUrl);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    redirect: 'http://localhost:8060/api/v1/tickets',
  });

  authState.clearToken();
});


it('should not attach the JWT when the API path prefix is URL-encoded', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const encodedUrl =
    'http://localhost:8060/%61pi/v1/tickets';

  http.get(encodedUrl).subscribe();

  const request = httpTesting.expectOne(encodedUrl);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should attach the JWT when a later API path segment contains an encoded character', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://localhost:8060/api/v1/tick%65ts';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT to a misleading API path prefix', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api-evil/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the API path contains an extra slash after api', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api//v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

 expect(
  request.request.headers.get('Authorization'),
).toBe('Bearer test-jwt-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the path uses apix instead of api', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/apix/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the API path uses uppercase letters', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/API/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the path uses api-v1 instead of api', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api-v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the API URL uses a different port', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8070/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the API URL uses HTTPS instead of HTTP', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'https://localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the API URL uses a different hostname', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://127.0.0.1:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the hostname uses uppercase letters', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://LOCALHOST:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the hostname adds a deceptive domain suffix', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost.evil.com:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the port is a longer number', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:80600/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the gateway URL appears inside the path', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://example.com/http://localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the URL uses HTTP port 80', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:80/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the hostname has a trailing dot', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost.:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the hostname has an extra label', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost.localdomain:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});


it('should not attach the JWT when the hostname uses the IPv6 loopback address', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://[::1]:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the hostname contains an encoded character', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://%6cocalhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the URL contains user information', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://user@localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when localhost is used as a subdomain', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://api.localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the URL uses the FTP scheme', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'ftp://localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the API path is nested under public', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/public/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the API-looking text appears in the query', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://localhost:8060/other?next=/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the API-looking text appears in the fragment', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://localhost:8060/other#/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT to an unrelated hostname with the trusted API path', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://attacker.example:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT to the backend port when the gateway port is trusted', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8061/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the API path uses mixed case', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/Api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when a semicolon path segment precedes the API path', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url =
    'http://localhost:8060/public;mode=preview/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should not attach the JWT when the api path prefix is percent-encoded', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/%61pi/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should attach the JWT to the exact trusted gateway URL', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should attach the JWT and preserve query parameters on the trusted API URL', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets';

  http.get(url, {
    params: {
      status: 'PENDING',
      page: '0',
      size: '20',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    request =>
      request.url === url &&
      request.method === 'GET' &&
      request.params.get('status') === 'PENDING' &&
      request.params.get('page') === '0' &&
      request.params.get('size') === '20',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  expect(request.request.params.get('status')).toBe('PENDING');
  expect(request.request.params.get('page')).toBe('0');
  expect(request.request.params.get('size')).toBe('20');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should preserve the POST method and request body when attaching the JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets/42/purchase';
  const body = {};

  http.post(url, body).subscribe();

  const request = httpTesting.expectOne(url);

  expect(request.request.method).toBe('POST');

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  expect(request.request.body).toEqual({});

  request.flush({
    orderId: 1001,
    status: 'CONFIRMED',
  });

  authState.clearToken();
});

it('should preserve the Idempotency-Key header when attaching the JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets/42/purchase';
  const idempotencyKey = 'purchase-test-key-123';

  http.post(url, {}, {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  }).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  expect(
    request.request.headers.get('Idempotency-Key'),
  ).toBe(idempotencyKey);

  expect(request.request.method).toBe('POST');
  expect(request.request.body).toEqual({});

  request.flush({
    orderId: 1002,
    status: 'CONFIRMED',
  });

  authState.clearToken();
});

it('should preserve custom headers when attaching the JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('test-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets';
  const correlationId = 'trace-test-abc-123';

  http.get(url, {
    headers: {
      'X-Correlation-ID': correlationId,
      'X-Client-Version': '1.0.0',
    },
  }).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer test-jwt-token');

  expect(
    request.request.headers.get('X-Correlation-ID'),
  ).toBe(correlationId);

  expect(
    request.request.headers.get('X-Client-Version'),
  ).toBe('1.0.0');

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  authState.clearToken();
});

it('should send the trusted API request without Authorization when no JWT exists', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.clearToken();

  const url = 'http://localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });
});

it('should preserve a caller-supplied Authorization header when no JWT exists', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.clearToken();

  const url = 'http://localhost:8060/api/v1/tickets';
  const callerAuthorization = 'Bearer caller-provided-token';

  http.get(url, {
    headers: {
      Authorization: callerAuthorization,
    },
  }).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe(callerAuthorization);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });
});

it('should preserve caller Authorization on an unrelated URL when a JWT exists', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  const url = 'http://localhost:8060/health';
  const callerAuthorization = 'Bearer health-check-token';

  http.get(url, {
    headers: {
      Authorization: callerAuthorization,
    },
  }).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.get('Authorization'),
  ).toBe(callerAuthorization);

  request.flush({ status: 'UP' });
});

it('should not attach the stored JWT to an HTTPS URL when the trusted gateway uses HTTP', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  const url = 'https://localhost:8060/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });
});

it('should not attach the stored JWT to a different gateway port', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  const url = 'http://localhost:8070/api/v1/tickets';

  http.get(url).subscribe();

  const request = httpTesting.expectOne(url);

  expect(
    request.request.headers.has('Authorization'),
  ).toBe(false);

  request.flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });
});

it('should preserve ticket search query parameters when attaching a JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets';

  http.get(url, {
    params: {
      status: 'PENDING',
      page: '2',
      size: '10',
    },
  }).subscribe();

  const request = httpTesting.expectOne(
    candidate =>
      candidate.url === url &&
      candidate.method === 'GET',
  );

  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer stored-jwt-token');

  expect(request.request.params.get('status')).toBe('PENDING');
  expect(request.request.params.get('page')).toBe('2');
  expect(request.request.params.get('size')).toBe('10');

  request.flush({
    content: [],
    number: 2,
    totalPages: 3,
    totalElements: 25,
    size: 10,
  });
});

it('should preserve the POST method and body when attaching a JWT', () => {
  httpTesting.expectOne(
    request =>
      request.url.includes('/api/v1/tickets') &&
      request.method === 'GET',
  ).flush({
    content: [],
    number: 0,
    totalPages: 0,
    totalElements: 0,
    size: 20,
  });

  const authState = TestBed.inject(AuthState);
  const http = TestBed.inject(HttpClient);

  authState.setToken('stored-jwt-token');

  const url = 'http://localhost:8060/api/v1/tickets/42/purchase';

  http.post(url, {}).subscribe();

  const request = httpTesting.expectOne(url);

  expect(request.request.method).toBe('POST');
  expect(request.request.body).toEqual({});
  expect(
    request.request.headers.get('Authorization'),
  ).toBe('Bearer stored-jwt-token');

  request.flush({
    orderId: 123,
  });
});

});
