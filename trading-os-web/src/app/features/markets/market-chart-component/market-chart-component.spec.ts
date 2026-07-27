import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MarketChartComponent } from './market-chart-component';

describe('MarketChartComponent', () => {
  let component: MarketChartComponent;
  let fixture: ComponentFixture<MarketChartComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketChartComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketChartComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
