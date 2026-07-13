import { TestBed } from '@angular/core/testing';

import { Account } from './account.service';

describe('AccountModel', () => {
  let service: Account;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(Account);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
