import { TestBed } from '@angular/core/testing';

import { MarketDataStreamService } from './market-data-stream.service';

describe('MarketDataStreamService', () => {
  let service: MarketDataStreamService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MarketDataStreamService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
