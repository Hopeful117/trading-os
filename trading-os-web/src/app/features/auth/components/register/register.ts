import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RegisterService } from '../../../../core/services/register';
import { RegisterRequest } from '../../../../core/models/register-request.model';
import { Router } from '@angular/router';

@Component({
  selector: 'app-register',
  imports: [FormsModule],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class RegisterComponent {
  private registerService = inject(RegisterService);
  private router = inject(Router);
  registerRequest: RegisterRequest = {
    username: '',
    email: '',
    password: '',
  };

  readonly loading = signal(false);
  readonly errorMessage = signal('');

  onSubmit(): void {
    if (this.loading()) {
      return;
    }
    this.errorMessage.set('');
    this.loading.set(true);

    this.registerService.register(this.registerRequest).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/login'], {
          state: { accountCreated: true, username: this.registerRequest.username },
        });
      },
      error: (error) => {
        this.loading.set(false);

        if (error.status === 409) {
          this.errorMessage.set('Ce nom d’utilisateur ou cet email est déjà utilisé.');
        } else if (error.status === 400) {
          this.errorMessage.set('Les informations saisies sont invalides.');
        } else {
          this.errorMessage.set('Une erreur est survenue lors de la création du compte.');
        }
      },
    });
  }
}
