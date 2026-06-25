import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MetadataResponse } from './models';

@Injectable({ providedIn: 'root' })
export class CaseService {
  private readonly http = inject(HttpClient);

  /**
   * Fetches form options and image constraints from the backend.
   * Response drives the caseType/equipmentCategory selectors and file validation.
   * Reference: ADR-002 §5, ADR-001 GET /api/metadata.
   */
  getMetadata(): Observable<MetadataResponse> {
    return this.http.get<MetadataResponse>('/api/metadata');
  }
}
