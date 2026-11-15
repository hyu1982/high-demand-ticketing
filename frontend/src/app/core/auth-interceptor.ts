import { inject, Service } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';

import { AuthState } from '../features/auth/auth-state';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

@Service()
export class AuthInterceptor {
  private readonly authState = inject(AuthState);
}

/*
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authState = inject(AuthState);
  const token = authState.getToken();

  if (!token || !req.url.startsWith(`${environment.apiUrl}/api/`)) {
    return next(req);
  }

  const authenticatedRequest = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedRequest);
};
*/
/*
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthState).getToken();

  if (!token || !req.url.startsWith(`${environment.apiUrl}/api/`)) {
    return next(req);
  }

  return next(
    req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }),
  );
};
*/
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authState = inject(AuthState);
  const router = inject(Router);
  const token = authState.getToken();

  const isApiRequest = req.url.startsWith(`${environment.apiUrl}/api/`);

  const request = token && isApiRequest
    ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      })
    : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && isApiRequest && !req.url.endsWith('/api/auth/login')) {
        authState.clearToken();
        void router.navigateByUrl('/login');
      }

      return throwError(() => error);
    }),
  );
};
