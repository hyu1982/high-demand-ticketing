import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TicketApi, TicketSearchCriteria, Order } from '../ticket-api';
import { Ticket } from '../ticket';
import { TicketPage } from '../ticket-page';
import { CurrencyPipe } from '@angular/common';
import { AuthState } from '../../auth/auth-state';
import { ActivatedRoute, NavigationStart, Router } from '@angular/router';
import {  FormControl,  FormGroup,  ReactiveFormsModule,  Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, finalize, filter, switchMap, tap } from 'rxjs';

type PurchaseState =
  | { status: 'idle' }
  | {
      status: 'purchasing';
      ticketId: number;
      idempotencyKey: string;
    }
  | { status: 'success'; orderId: number }
  | { status: 'failed'; message: string }
  | {
      status: 'unknown';
      ticketId: number;
      message: string;
      idempotencyKey: string;
    };

@Component({
  imports: [CurrencyPipe, FormsModule, ReactiveFormsModule],
  selector: 'app-ticket-list',
  styleUrls: ['./ticket-list.scss'],
  templateUrl: './ticket-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TicketList {
  private readonly ticketApi = inject(TicketApi);
  private readonly authState = inject(AuthState);
  private readonly router = inject(Router);
  protected readonly tickets = signal<Ticket[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly currentPage = signal(0);
  protected readonly pageSize = signal(2);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly statusFilter = signal('');
  protected readonly eventNameFilter = signal('');
  protected readonly maxPriceFilter = signal<string>('');
  protected readonly purchasingTicketIds = signal<number[]>([]);
  protected readonly purchaseMessage = signal<string | null>(null);
  protected readonly purchaseError = signal<string | null>(null);
  protected readonly purchaseState = signal<PurchaseState>({
  status: 'idle',
});
private searchRequestId = 0;
private readonly route = inject(ActivatedRoute);
private isHistoryNavigation = false;

constructor() {

  this.router.events
  .pipe(
    filter(
      (event): event is NavigationStart =>
        event instanceof NavigationStart,
    ),
    takeUntilDestroyed(),
  )
  .subscribe((event) => {
    this.isHistoryNavigation =
      event.navigationTrigger === 'popstate';
  });

  this.ticketRequests
    .pipe(
      // Increment before switchMap cancels the previous request.
      tap(() => {
        this.searchRequestId++;
      }),

      switchMap((request) => {
        const requestId = this.searchRequestId;

        this.loading.set(true);
        this.error.set(null);

        return this.ticketApi
          .getTickets(request.page, request.size, request.criteria)
          .pipe(
            tap((page: TicketPage) => {
              this.tickets.set(page.content);
              this.currentPage.set(page.number);
              this.totalPages.set(page.totalPages);
              this.totalElements.set(page.totalElements);
            }),

            catchError(() => {
              this.error.set(
                'Unable to load tickets. Please try again later.',
              );
              return EMPTY;
            }),

            finalize(() => {
              // Only the latest request may clear the loading state.
              if (requestId === this.searchRequestId) {
                this.loading.set(false);
              }
            }),
          );
      }),

      takeUntilDestroyed(),
    )
    .subscribe();

  // Intentionally do not call loadTickets() here.
  // Tickets load when the user initiates a search.

  this.route.queryParamMap
  .pipe(takeUntilDestroyed())
  .subscribe((params) => {
    const parsed = this.parseSearchParams({
      eventName: params.get('eventName'),
      status: params.get('status'),
      maxPrice: params.get('maxPrice'),
      page: params.get('page'),
    });

    this.searchForm.patchValue({
      eventName: parsed.eventName,
      status: parsed.status,
      maxPrice: parsed.maxPrice,
    });

    this.eventNameFilter.set(parsed.eventName);
    this.statusFilter.set(parsed.status);
    this.maxPriceFilter.set(parsed.maxPrice === null ? '' : String(parsed.maxPrice),);
    this.currentPage.set(parsed.page);

    if (this.isHistoryNavigation) {
  this.isHistoryNavigation = false;
  this.loadTickets();
}

  });
}

private parseSearchParams(params: {
  eventName: string | null;
  status: string | null;
  maxPrice: string | null;
  page: string | null;
}): {
  eventName: string;
  status: string;
  maxPrice: number | null;
  page: number;
} {
  const eventName = (params.eventName ?? '').trim().slice(0, 100);

  const allowedStatuses = ['PENDING', 'SOLD', 'CANCELLED'];
  const status = allowedStatuses.includes(params.status ?? '')
    ? params.status!
    : '';

  const parsedPrice =
  params.maxPrice !== null && params.maxPrice.trim() !== ''
    ? Number(params.maxPrice)
    : null;

const maxPrice =
  parsedPrice !== null &&
  Number.isFinite(parsedPrice) &&
  parsedPrice >= 0
    ? parsedPrice
    : null;

  const parsedPage = Number(params.page);
  const page =
    params.page !== null &&
    /^\d+$/.test(params.page) &&
    Number.isSafeInteger(parsedPage)
      ? parsedPage
      : 0;

  return { eventName, status, maxPrice, page };
}


  protected loadTickets(): void {
  const rawMaxPrice = this.maxPriceFilter().trim();
  const parsedMaxPrice =
    rawMaxPrice === '' ? null : Number(rawMaxPrice);

  if (
    parsedMaxPrice !== null &&
    (!Number.isFinite(parsedMaxPrice) || parsedMaxPrice < 0)
  ) {
    this.error.set(
      'Maximum price must be a valid non-negative number.',
    );
    return;
  }

  const criteria: TicketSearchCriteria = {
    status: this.statusFilter(),
    eventName: this.eventNameFilter(),
    maxPrice: parsedMaxPrice,
  };

  this.ticketRequests.next({
    page: this.currentPage(),
    size: this.pageSize(),
    criteria,
  });
}

  private readonly ticketRequests = new Subject<{
  page: number;
  size: number;
  criteria: TicketSearchCriteria;
}>();

protected previousPage(): void {
  if (this.currentPage() > 0 && !this.loading()) {
    const page = this.currentPage() - 1;

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge',
    });

    this.currentPage.set(page);
    this.loadTickets();
  }
}

protected nextPage(): void {
  if (
    this.currentPage() + 1 < this.totalPages() &&
    !this.loading()
  ) {
    const page = this.currentPage() + 1;

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge',
    });

    this.currentPage.set(page);
    this.loadTickets();
  }
}


  protected logout(): void {
    this.authState.clearToken();
    void this.router.navigateByUrl('/login');
  }

  protected applyFilters(): void {
    this.currentPage.set(0);
    this.loadTickets();
  }   


  readonly searchForm = new FormGroup({
  eventName: new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(100)]
  }),
  status: new FormControl('', {
    nonNullable: true
  }),
  maxPrice: new FormControl<number | null>(null, {
    validators: [Validators.min(0)]
  })
});

protected onSearchSubmit(): void {
  if (this.searchForm.invalid) {
    this.searchForm.markAllAsTouched();
    return;
  }

  const values = this.searchForm.getRawValue();

  // Transfer validated form values into the existing filter state.
  this.eventNameFilter.set(values.eventName.trim());
  this.statusFilter.set(values.status);
  this.maxPriceFilter.set(
    values.maxPrice === null ? '' : String(values.maxPrice),
  );

  // Update the URL to represent the submitted search.
  void this.router.navigate([], {
    relativeTo: this.route,
    queryParams: {
      eventName: this.eventNameFilter() || null,
      status: this.statusFilter() || null,
      maxPrice: this.maxPriceFilter() || null,
      page: 0,
    },
    queryParamsHandling: 'merge',
  });

  // Reuse existing behavior: reset to page zero and load from the API.
  this.applyFilters();
}

protected clearFilters(): void {
  this.searchForm.reset({
    eventName: '',
    status: '',
    maxPrice: null,
  });

  this.eventNameFilter.set('');
  this.statusFilter.set('');
  this.maxPriceFilter.set('');

  this.currentPage.set(0);
  this.totalPages.set(0);
  this.totalElements.set(0);

  this.tickets.set([]);
  this.error.set(null);
  this.loading.set(false);

  void this.router.navigate([], {
  relativeTo: this.route,
  queryParams: {
    eventName: null,
    status: null,
    maxPrice: null,
    page: null,
  },
  queryParamsHandling: 'merge',
});
}

protected purchaseTicket(ticketId: number): void {
  // Block duplicate clicks while the request is processing.
  if (this.purchasingTicketIds().includes(ticketId)) {
    return;
  }

  // Do not start a new purchase while the previous outcome is unknown.
  const currentPurchase = this.purchaseState();

  if (
    currentPurchase.status === 'unknown' &&
    this.purchasingTicketIds().includes(ticketId)
  ) {
    return;
  }

  this.purchaseMessage.set(null);
  this.purchaseError.set(null);

  this.purchasingTicketIds.update(ids => [...ids, ticketId]);

  // Generate one key for this logical purchase.
  const idempotencyKey = crypto.randomUUID();

  this.purchaseState.set({
    status: 'purchasing',
    ticketId,
    idempotencyKey,
  });

    this.sendPurchaseRequest(ticketId, idempotencyKey);    
  }

  private sendPurchaseRequest(ticketId: number,  idempotencyKey: string): void {
    // Move the existing purchaseTicket() HTTP subscription here.
    this.ticketApi
    .purchaseTicket(ticketId, idempotencyKey)
    .subscribe({
      next: (order: Order) => {
         this.purchaseState.set({
            status: 'success',
            orderId: order.id,
          });


        this.purchaseMessage.set(
          `Ticket purchased successfully. Order ID: ${order.id}`,
        );

        this.purchaseError.set(null);

        this.purchasingTicketIds.update(
          ids => ids.filter(id => id !== ticketId),
        );

        this.loadTickets();
      },
      error: (error) => {
      if (error.status === 409) {
        this.purchasingTicketIds.update(
        ids => ids.filter(id => id !== ticketId),
      );

        this.purchaseState.set({
          status: 'failed',
          message: 'This ticket is no longer available.',
        });

        this.purchaseError.set(
          'This ticket is no longer available. Refreshing tickets.',
        );

        this.loadTickets();
      } else if (error.status === 403) {
        this.purchasingTicketIds.update(
        ids => ids.filter(id => id !== ticketId),
      );
        this.purchaseState.set({
          status: 'failed',
          message: 'You are not authorized to purchase tickets.',
        });

        this.purchaseError.set(
          'You are not authorized to purchase tickets.',
        );
      } else if (error.status === 0 || error.status >= 500) {
        this.purchaseState.set({
          status: 'unknown',
          message:
            'We could not confirm whether your purchase succeeded. Check your order status before trying again.',
            ticketId,
          idempotencyKey,
        });

        this.purchaseError.set(
          'Purchase status is unknown. Check your order status before trying again.',
        );
      } else {

        this.purchasingTicketIds.update(
        ids => ids.filter(id => id !== ticketId),
      );

        this.purchaseState.set({
          status: 'failed',
          message: 'Unable to purchase this ticket.',
        });

        this.purchaseError.set(
          'Unable to purchase this ticket. Please try again.',
        );
      }
    },

    });
  }

  protected retryUnknownPurchase(): void {
      const currentPurchase = this.purchaseState();

      if (currentPurchase.status !== 'unknown') {
        return;
      }

      const { ticketId, idempotencyKey } = currentPurchase;

      // if (this.purchasingTicketIds().includes(ticketId)) {
      //   return;
      // }

      this.purchaseMessage.set(null);
      this.purchaseError.set(null);

      this.purchasingTicketIds.update(ids => [...ids, ticketId]);

      this.purchaseState.set({
        status: 'purchasing',
        ticketId,
        idempotencyKey,
      });

      this.sendPurchaseRequest(ticketId, idempotencyKey);
    }
}
