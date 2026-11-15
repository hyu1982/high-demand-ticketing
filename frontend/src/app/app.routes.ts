import { Routes } from '@angular/router';
import { TicketList } from './features/tickets/ticket-list/ticket-list';
import { Login } from './features/auth/login/login';
import { authGuard } from './core/auth-guard';

// export const routes: Routes = [
//   { path: 'login', component: Login },
//   { path: 'tickets', component: TicketList, canActivate: [authGuard] },
//   { path: '', redirectTo: 'login', pathMatch: 'full' },
//   { path: '**', redirectTo: 'login' },
// ];

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login')
        .then(m => m.Login),
  },
  {
    path: 'tickets',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/tickets/ticket-list/ticket-list')
        .then(m => m.TicketList),
  },
  {
  path: 'tickets2',
  canActivate: [authGuard],
  loadComponent: () =>
    import('./features/tickets/ticket-list-signal/ticket-list-signal')
      .then(m => m.TicketListSignal),
},
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
