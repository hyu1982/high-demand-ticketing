import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';

import { AuthApi } from '../auth-api';
import { AuthState } from '../auth-state';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})

export class Login {
  private readonly authApi = inject(AuthApi);
  private readonly authState = inject(AuthState);

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly success = signal(false);

  private readonly router = inject(Router);

  protected login(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.success.set(false);

    this.authApi
      .login({
        username: this.username(),
        password: this.password(),
      })
      .subscribe({
        next: (response) => {
            this.authState.setToken(response.token);
            this.success.set(true);

            this.loading.set(false);
            
            this.router.navigate(['/tickets2']);
          },
        error: () => {
          this.error.set('Login failed. Check your credentials and try again.');
          this.loading.set(false);
        },
      });
  }
}
