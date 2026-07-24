import { ComponentFixture, TestBed } from '@angular/core/testing';

import {MarketDetail} from './markets-details';

describe('MarketsDetails', () => {
  let component: MarketDetail;
  let fixture: ComponentFixture<MarketDetail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MarketDetail],
    }).compileComponents();

    fixture = TestBed.createComponent(MarketDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
