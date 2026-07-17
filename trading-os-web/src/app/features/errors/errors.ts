import { Component } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  selector: 'app-error',
  imports: [RouterLink],
  templateUrl: './errors.html',
  styleUrl: './errors.scss',
})
export class ErrorPage {
  status = 500;
  title = 'Erreur inconnue';
  message = 'Une erreur est survenue.';

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.status = Number(this.route.snapshot.queryParamMap.get('status')) || 500;

    this.message =
      this.route.snapshot.queryParamMap.get('message') || this.getDefaultMessage(this.status);

    this.title = this.getTitle(this.status);
  }

  private getTitle(status: number): string {
    switch (status) {
      case 401:
        return 'Session expirée';

      case 403:
        return 'Accès refusé';

      case 404:
        return 'Ressource introuvable';

      case 503:
        return 'Service indisponible';

      default:
        return 'Erreur';
    }
  }

  private getDefaultMessage(status: number): string {
    switch (status) {
      case 401:
        return 'Votre session a expiré, veuillez vous reconnecter.';

      case 403:
        return 'Vous n’avez pas les permissions nécessaires.';

      case 404:
        return 'La ressource demandée est introuvable.';

      case 503:
        return 'Le service est temporairement indisponible.';

      default:
        return 'Une erreur inattendue est survenue.';
    }
  }
}
