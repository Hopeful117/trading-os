import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MarketToolbarComponent } from './market-toolbar-component';

describe('MarketToolbarComponent', () => {
  let component: MarketToolbarComponent;
  let fixture: ComponentFixture<MarketToolbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketToolbarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketToolbarComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
