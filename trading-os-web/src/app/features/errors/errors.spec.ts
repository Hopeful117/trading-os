import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';

import { ErrorPage } from './errors';

describe('Errors', () => {
  let component: ErrorPage;
  let fixture: ComponentFixture<ErrorPage>;

  function setup(queryParams: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [ErrorPage],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: new Map(Object.entries(queryParams)),
            },
          },
        },
      ],
    });

    fixture = TestBed.createComponent(ErrorPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', () => {
    setup();
    expect(component).toBeTruthy();
  });

  it('should show default 500 error when no status param', () => {
    setup();
    expect(component.status).toBe(500);
    expect(component.title).toBe('Erreur');
    expect(component.message).toBe('Une erreur inattendue est survenue.');
  });

  it('should show 401 error with specific message', () => {
    setup({ status: '401' });
    expect(component.status).toBe(401);
    expect(component.title).toBe('Session expirée');
    expect(component.message).toBe('Votre session a expiré, veuillez vous reconnecter.');
  });

  it('should show 403 error with specific message', () => {
    setup({ status: '403' });
    expect(component.status).toBe(403);
    expect(component.title).toBe('Accès refusé');
    expect(component.message).toContain('permissions');
  });

  it('should show 404 error with specific message', () => {
    setup({ status: '404' });
    expect(component.status).toBe(404);
    expect(component.title).toBe('Ressource introuvable');
    expect(component.message).toBe('La ressource demandée est introuvable.');
  });

  it('should show 503 error with specific message', () => {
    setup({ status: '503' });
    expect(component.status).toBe(503);
    expect(component.title).toBe('Service indisponible');
    expect(component.message).toBe('Le service est temporairement indisponible.');
  });

  it('should use custom message from query param when provided', () => {
    setup({ status: '422', message: 'Custom error detail' });
    expect(component.status).toBe(422);
    expect(component.message).toBe('Custom error detail');
  });

  it('should fall back to default for unknown status', () => {
    setup({ status: '418' });
    expect(component.status).toBe(418);
    expect(component.title).toBe('Erreur');
    expect(component.message).toBe('Une erreur inattendue est survenue.');
  });
});
