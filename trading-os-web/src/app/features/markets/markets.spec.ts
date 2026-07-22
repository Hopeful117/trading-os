import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Markets } from './markets';
import { MarketService } from '../../core/services/market.service';
import { of } from 'rxjs';

describe('Markets', () => {
  let component: Markets;
  let fixture: ComponentFixture<Markets>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Markets],
      providers: [{ provide: MarketService, useValue: { findAll: () => of([]) } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Markets);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
