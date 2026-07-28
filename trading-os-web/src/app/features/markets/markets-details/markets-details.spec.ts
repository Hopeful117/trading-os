import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, ActivatedRoute } from '@angular/router';
import { NEVER, of } from 'rxjs';

import { MarketDataStreamService } from '../../../core/services/market-data-stream.service';
import { MarketService } from '../../../core/services/market.service';
import { MarketDetail } from './markets-details';

describe('MarketsDetails', () => {
  let component: MarketDetail;
  let fixture: ComponentFixture<MarketDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketDetail],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({})),
          },
        },
        {
          provide: MarketService,
          useValue: {
            findById: () => NEVER,
            subscribe: () => of(undefined),
            unsubscribe: () => of(undefined),
            findOhlcHistory: () => of([]),
          },
        },
        {
          provide: MarketDataStreamService,
          useValue: {
            streamTicker: () => NEVER,
            streamOhlc: () => NEVER,
            streamOrderBook: () => NEVER,
            streamRecentTrades: () => NEVER,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
