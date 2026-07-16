import { Component } from '@angular/core';
import { AccountService } from '../../../../core/services/account.service';
import { Account } from '../../../../core/models/account.model';
import { AccountCard} from '../../components/account-card/account-card';
import { ChangeDetectorRef} from '@angular/core';


@Component({
  selector: 'app-accounts',
  imports: [AccountCard],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class Accounts {
  accounts: Account[] = [];
  loadingSync = false;
  errorMessage = '';

  constructor(
    private accountService: AccountService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadAccounts();
  }

  private loadAccounts(): void {
    this.accountService.getAccounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.cdr.detectChanges();
      },
      error: (error) => {
        this.loadingSync = false;

        if (error.status === 401) {
          this.errorMessage = 'Votre session a expiré, veuillez vous reconnecter.';
        } else if (error.status === 403) {
          this.errorMessage = 'Vous n’avez pas les droits pour accéder à ces comptes.';
        } else if (error.status === 404) {
          this.errorMessage = 'Aucun compte trouvé.';
        } else if (error.status === 503) {
          this.errorMessage = 'Le service de synchronisation est indisponible.';
        } else {
          this.errorMessage = 'Une erreur est survenue lors du chargement des comptes.';
        }
      },
    });
  }
  sync(): void {
    this.loadingSync = true;

    this.accountService.synchronize().subscribe({
      next: () => {
        this.loadingSync = false;
        this.loadAccounts();
      },

      error: () => {
        this.loadingSync = false;
      },
    });
  }
}
