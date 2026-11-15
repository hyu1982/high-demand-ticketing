import { inject, Service } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TicketPage } from './ticket-page';

export interface TicketSearchCriteria {
  status?: string;
  eventName?: string;
  maxPrice?: number | null;
}

export interface Order {
  id: number;
}

@Service()
export class TicketApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/v1/tickets`;

  getTickets(
    page: number = 0,
    size: number = 3,
    criteria: TicketSearchCriteria = {},
  ): Observable<TicketPage> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');

    if (criteria.status) {
      params = params.set('status', criteria.status);
    }

    if (criteria.eventName?.trim()) {
      params = params.set('eventName', criteria.eventName.trim());
    }

    if (criteria.maxPrice != null) {
      params = params.set('maxPrice', criteria.maxPrice);
    }

    return this.http.get<TicketPage>(this.baseUrl, { params });
  }

  purchaseTicket(ticketId: number, idempotencyKey: string): Observable<Order> {
    return this.http.post<Order>(
      `${this.baseUrl}/${ticketId}/purchase`,
      {},
      {
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
      },
    );
  }
}
