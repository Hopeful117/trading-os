import { Component, inject } from '@angular/core';
import { MarketResponse } from '../../core/models/market-response';
import { Observable } from 'rxjs';
import { MarketService } from '../../core/services/market.service';
import { AsyncPipe } from '@angular/common';
import {Router} from '@angular/router';

@Component({
  selector: 'app-markets',
  imports: [AsyncPipe],
  templateUrl: './markets.html',
  styleUrl: './markets.scss',
})
export class Markets {
  private marketService = inject(MarketService);
  private readonly router = inject(Router);

  markets$!: Observable<MarketResponse[]>;

  ngOnInit(): void {
    this.markets$ = this.marketService.findAll();
  }
  openMarket(marketId: string): void {
    void this.router.navigate(['/markets', marketId]);
  }
}
