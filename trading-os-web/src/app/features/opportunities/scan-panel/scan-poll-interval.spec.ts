import { TestBed } from '@angular/core/testing';

import { SCAN_POLL_INTERVAL_MS } from './scan-poll-interval';

describe('SCAN_POLL_INTERVAL_MS', () => {
  it('provides a two-second default poll interval', () => {
    TestBed.configureTestingModule({});

    expect(TestBed.inject(SCAN_POLL_INTERVAL_MS)).toBe(2000);
  });
});
