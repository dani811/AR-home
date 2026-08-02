import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateSpaceRequest,
  FurnitureInstanceDto,
  RegisterFurnitureRequest,
  SpaceDto
} from './spatial.models';

@Injectable({ providedIn: 'root' })
export class SpatialApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/spatial';

  createSpace(request: CreateSpaceRequest): Observable<SpaceDto> {
    return this.http.post<SpaceDto>(`${this.baseUrl}/spaces`, request);
  }

  registerFurniture(
    spaceId: string,
    request: RegisterFurnitureRequest
  ): Observable<FurnitureInstanceDto> {
    return this.http.post<FurnitureInstanceDto>(
      `${this.baseUrl}/spaces/${encodeURIComponent(spaceId)}/furniture`,
      request
    );
  }
}
