import { Service, signal } from '@angular/core';

@Service()
export class AuthState {
  private readonly storageKey = 'accessToken';
  
  private readonly accessToken = signal<string | null>(
    this.loadToken()
  );

  private loadToken(): string | null {
    try {
      return sessionStorage.getItem(this.storageKey);
    } catch {
      return null;
    }
  }

  getToken(): string | null {
    return this.accessToken();
  }

  setToken(token: string): void {
    this.accessToken.set(token);

    try {
      sessionStorage.setItem(this.storageKey, token);
    } catch {
      // The in-memory token remains available for this page.
    }
  }

  clearToken(): void {
    this.accessToken.set(null);

    try {
      sessionStorage.removeItem(this.storageKey);
    } catch {
      // The in-memory token is still cleared.
    }
  }

  isAuthenticated(): boolean {
    return this.accessToken() !== null;
  }
}
