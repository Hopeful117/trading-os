import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { routes } from './app.routes';

describe('app routes', () => {
  function findRoute(path: string) {
    return routes.find((route) => route.path === path);
  }

  it('exposes the opportunities list route behind authentication', () => {
    const route = findRoute('opportunities');

    expect(route).toBeDefined();
    expect(route?.canActivate).toContain(authGuard);
    expect(route?.component).toBeDefined();
  });

  it('exposes the opportunity detail route behind authentication', () => {
    const route = findRoute('opportunities/:opportunityId');

    expect(route).toBeDefined();
    expect(route?.canActivate).toContain(authGuard);
    expect(route?.component).toBeDefined();
  });

  it('keeps every trader-facing feature route authenticated', () => {
    const traderPaths: Routes = routes.filter(
      (route) =>
        typeof route.path === 'string' &&
        ['dashboard', 'accounts', 'markets', 'markets/:marketId', 'opportunities'].includes(
          route.path,
        ),
    );

    expect(traderPaths.length).toBe(5);
    expect(traderPaths.every((route) => route.canActivate?.includes(authGuard))).toBe(true);
  });
});
