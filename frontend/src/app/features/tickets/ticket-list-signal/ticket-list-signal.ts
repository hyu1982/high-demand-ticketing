
import { CurrencyPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import {
  form,
  FormField,
  validate
} from '@angular/forms/signals';

import { TicketApi, TicketSearchCriteria } from '../ticket-api';
import { Ticket } from '../ticket';
import { HttpErrorResponse } from '@angular/common/http';

interface TicketSearchModel {
  status: string;
  eventName: string;
  maxPrice: number | null;
}

type PurchaseState =
  | { status: 'idle' }
  | {
      status: 'purchasing';
      ticketId: number;
      idempotencyKey: string;
    }
  | {
      status: 'success';
      orderId: number;
    }
  | {
      status: 'failed';
      message: string;
    }
  | {
      status: 'unknown';
      ticketId: number;
      idempotencyKey: string;
      message: string;
    };

@Component({
  selector: 'app-ticket-list-signal',
  standalone: true,
  imports: [CurrencyPipe, FormField],
  templateUrl: './ticket-list-signal.html'
})
export class TicketListSignal {
  private readonly ticketApi = inject(TicketApi);
  readonly purchasingTicketIds = signal<Set<number>>(new Set());

readonly purchaseState = signal<PurchaseState>({
  status: 'idle'
});

  // The signal is the source of truth for form values.
  readonly searchModel = signal<TicketSearchModel>({
    status: '',
    eventName: '',
    maxPrice: null
  });

  // Signal Forms owns field state and validation.
  readonly searchForm = form(this.searchModel, path => {
    validate(path.maxPrice, ({ value }) => {
      const price = value();

      if (price === null) {
        return null;
      }

      if (!Number.isFinite(price) || price < 0) {
        return {
          kind: 'invalidMaxPrice',
          message: 'Maximum price must be zero or greater.'
        };
      }

      return null;
    });
  });

  readonly tickets = signal<Ticket[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly currentPage = signal(0);
  readonly pageSize = 3;
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  constructor() {
    this.loadTickets();
  }

  onSearch(event: Event): void {
    event.preventDefault();

    // Mark fields as touched so validation errors become visible.
    this.searchForm().markAsTouched();

    if (this.searchForm().invalid()) {
      return;
    }

    this.currentPage.set(0);
    this.loadTickets();
  }

  clearFilters(): void {
    this.searchForm().reset({
      status: '',
      eventName: '',
      maxPrice: null
    });

    this.currentPage.set(0);
    this.loadTickets();
  }

  goToPage(page: number): void {
    if (
      page < 0 ||
      page >= this.totalPages() ||
      page === this.currentPage()
    ) {
      return;
    }

    this.currentPage.set(page);
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading.set(true);
    this.error.set(null);

    const model = this.searchModel();

    const criteria: TicketSearchCriteria = {
      status: model.status || undefined,
      eventName: model.eventName.trim() || undefined,
      maxPrice: model.maxPrice
    };

    this.ticketApi
      .getTickets(this.currentPage(), this.pageSize, criteria)
      .subscribe({
        next: page => {
          this.tickets.set(page.content);
          this.totalPages.set(page.totalPages);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(
            'Unable to load tickets. Please try again later.'
          );
          this.loading.set(false);
        }
      });
  }

  purchaseTicket(ticketId: number): void {
  if (this.purchasingTicketIds().has(ticketId)) {
    return;
  }

  this.purchaseState.set({ status: 'idle' });

  const idempotencyKey = crypto.randomUUID();

  this.purchasingTicketIds.update(current => {
    const next = new Set(current);
    next.add(ticketId);
    return next;
  });

  this.sendPurchaseRequest(ticketId, idempotencyKey);
}

retryUnknownPurchase(): void {
  const state = this.purchaseState();

  if (state.status !== 'unknown') {
    return;
  }

  // Reuse the original key because the first request's
  // outcome is uncertain.
  this.sendPurchaseRequest(
    state.ticketId,
    state.idempotencyKey
  );
}

private sendPurchaseRequest(
  ticketId: number,
  idempotencyKey: string
): void {
  this.purchaseState.set({
    status: 'purchasing',
    ticketId,
    idempotencyKey
  });

  this.ticketApi
    .purchaseTicket(ticketId, idempotencyKey)
    .subscribe({
      next: order => {
        this.purchaseState.set({
          status: 'success',
          orderId: order.id
        });

        this.releasePurchaseLock(ticketId);
        this.loadTickets();
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 409) {
          this.purchaseState.set({
            status: 'failed',
            message:
              'This ticket is no longer available. The ticket list will be refreshed.'
          });

          this.releasePurchaseLock(ticketId);
          this.loadTickets();
          return;
        }

        if (error.status === 0 || error.status >= 500) {
          this.purchaseState.set({
            status: 'unknown',
            ticketId,
            idempotencyKey,
            message:
              'The purchase outcome could not be confirmed. Retry using the same request key.'
          });

          // Keep the ticket locked until the outcome is resolved.
          return;
        }

        this.purchaseState.set({
          status: 'failed',
          message:
            error.status === 403
              ? 'You are not authorized to purchase this ticket.'
              : 'The purchase request was rejected.'
        });

        this.releasePurchaseLock(ticketId);
      }
    });
}

/*
It creates a new Set containing the same elements as current.
A JavaScript Set is a collection that stores unique values. It is similar to a HashSet<T> in C# or a HashSet<T> in Java.
The important part is that next is a separate collection. Removing an item from next does not mutate the original current collection.
This tells Angular's signal to use the new collection as its updated value.
The entire method therefore removes the specified ticket ID from the set of tickets currently being purchased.
*/
private releasePurchaseLock(ticketId: number): void {
  this.purchasingTicketIds.update(current => {
    const next = new Set(current);
    next.delete(ticketId);
    return next;
  });
}
}
