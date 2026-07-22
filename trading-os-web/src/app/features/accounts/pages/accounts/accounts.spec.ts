import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Accounts } from './accounts';
import { AccountService } from '../../../../core/services/account.service';
import { of } from 'rxjs';

describe('Accounts', () => {
  let component: Accounts;
  let fixture: ComponentFixture<Accounts>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Accounts],
      providers: [{ provide: AccountService, useValue: { getAccounts: () => of([]), synchronize: () => of('ok') } }],
    }).compileComponents();

    fixture = TestBed.createComponent(Accounts);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
