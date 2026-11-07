import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { routes } from './app.routes';

describe('app routes', () => {
  function findRoute(path: string) {
    return routes.find((route) => route.path === path);
  }

  it.each([['opportunities'], ['opportunities/:opportunityId'], ['analytics'], ['agents']])(
    'exposes the %s route behind authentication',
    (path) => {
      const route = findRoute(path);

      expect(route).toBeDefined();
      expect(route?.canActivate).toContain(authGuard);
      expect(route?.component).toBeDefined();
    },
  );

  it('keeps every trader-facing feature route authenticated', () => {
    const traderPaths: Routes = routes.filter(
      (route) =>
        typeof route.path === 'string' &&
        [
          'dashboard',
          'accounts',
          'agents',
          'markets',
          'markets/:marketId',
          'opportunities',
        ].includes(route.path),
    );

    expect(traderPaths.length).toBe(6);
    expect(traderPaths.every((route) => route.canActivate?.includes(authGuard))).toBe(true);
  });
});
