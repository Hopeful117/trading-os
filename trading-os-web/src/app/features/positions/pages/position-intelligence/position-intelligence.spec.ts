import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { PositionService } from '../../../../core/services/position.service';
import { PositionIntelligence } from './position-intelligence';

describe('PositionIntelligence', () => {
  it('formats scenario values and maps display states', () => {
    const component = createComponent();

    expect(component.scenarioValue(null, 'USD')).toBe('Indisponible');
    expect(component.scenarioValue(12.5, 'USD')).toBe('12.50 USD');
    expect(component.scenarioValue(12.5, null)).toBe('12.50');
    expect(component.scenarioClass({ availability: 'AVAILABLE' } as never)).toBe('available');
    expect(component.sourceLabel('TRADING_CORE')).toBe('PAPER · Trading Core');
    expect(component.sourceLabel('BROKER_SERVICE')).toBe('LIVE · Broker Service');
    expect(component.freshness('FRESH')).toBe('LIVE');
    expect(component.freshness('STALE')).toBe('STALE');
    expect(component.freshness('UNKNOWN')).toBe('UNAVAILABLE');
    expect(component.statusTone('AVAILABLE')).toBe('positive');
    expect(component.statusTone('PARTIAL')).toBe('warning');
  });
});

function createComponent(): PositionIntelligence {
  TestBed.configureTestingModule({
    imports: [PositionIntelligence],
    providers: [
      {
        provide: ActivatedRoute,
        useValue: { queryParamMap: of(convertToParamMap({})) },
      },
      {
        provide: PositionService,
        useValue: { getPositionIntelligence: () => of(null) },
      },
    ],
  });

  return TestBed.createComponent(PositionIntelligence).componentInstance;
}
