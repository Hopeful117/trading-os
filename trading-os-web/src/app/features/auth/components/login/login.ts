import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../../core/services/auth.service';
import { LoginRequest } from '../../../../core/models/login-request.model';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  loginRequest: LoginRequest = {
    username: '',
    password: '',
  };

  readonly accountCreated = signal(
    Boolean(
      (this.router.getCurrentNavigation()?.extras.state as { accountCreated?: boolean } | undefined)
        ?.accountCreated,
    ),
  );
  readonly errorMessage = signal('');

  onSubmit(): void {
    this.errorMessage.set('');
    this.accountCreated.set(false);

    this.authService.login(this.loginRequest).subscribe({
      next: () => {
        this.router.navigate(['/']);
      },
      error: () => {
        this.errorMessage.set('Nom d’utilisateur ou mot de passe incorrect.');
      },
    });
  }
}
