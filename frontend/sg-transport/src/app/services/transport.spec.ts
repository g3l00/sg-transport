import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';

import { TransportService } from './transport';

describe('TransportService', () => {
  let service: TransportService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient()],
    });
    service = TestBed.inject(TransportService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
