import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Markets } from './markets';

describe('Markets', () => {
  let component: Markets;
  let fixture: ComponentFixture<Markets>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Markets],
    }).compileComponents();

    fixture = TestBed.createComponent(Markets);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
