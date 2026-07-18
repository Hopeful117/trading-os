import { Component, inject } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import {RegisterService} from '../../../../core/services/register';
import {RegisterRequest} from '../../../../core/models/register-request.model';
import {Router} from '@angular/router';

@Component({
  selector: 'app-register',
  imports: [FormsModule],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class RegisterComponent {
  private registerService=inject(RegisterService);
  private router = inject(Router);
  registerRequest: RegisterRequest = {
    username: '',
    email: '',
    password: '',
  };
  errorMessage = '';

  loading = false;

  onSubmit(): void {
    this.errorMessage = '';
    this.loading = true;

    this.registerService.register(this.registerRequest).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/login'])
      },

      error: (error) => {
        this.loading = false;

        if (error.status === 409) {
          this.errorMessage = 'Ce nom d’utilisateur ou cet email est déjà utilisé.';
        } else if (error.status === 400) {
          this.errorMessage = 'Les informations saisies sont invalides.';
        } else {
          this.errorMessage = 'Une erreur est survenue lors de la création du compte.';
        }
      },
    });
  }
}
