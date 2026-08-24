import { InjectionToken } from '@angular/core';

/**
 * Interval between Active Scan projection polls while a scan session is
 * tracked by the scan panel. Overridable in tests.
 */
export const SCAN_POLL_INTERVAL_MS = new InjectionToken<number>('SCAN_POLL_INTERVAL_MS', {
  factory: () => 2000,
});
