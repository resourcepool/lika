import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import {
  Permalink,
  PermalinkCreator,
  PageRequest,
  PageResponse,
  SortDirection,
  SortProperty,
  ApiError
} from '../models/permalink.model';

@Injectable({
  providedIn: 'root'
})
export class PermalinkService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/links`;
  private readonly baseUrlAdmin = `${environment.apiBaseUrl}/api/admin/links`;

  /**
   * Get all permalinks for a domain (sync endpoint)
   */
  getAllPermalinks(domainId: string, admin = false): Observable<Permalink[]> {
    const params = new HttpParams().set('domainId', domainId);

    return this.http.get<Permalink[]>(`${admin ? this.baseUrlAdmin : this.baseUrl}/sync`, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Get paginated permalinks for a domain
   */
  getPermalinksPage(domainId: string | null, pageRequest: Partial<PageRequest> = {}, admin = false): Observable<PageResponse<Permalink>> {
    const params = this.buildPageParams(domainId, pageRequest);

    return this.http.get<PageResponse<Permalink>>(admin ? this.baseUrlAdmin : this.baseUrl, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Check if a slug is available for a domain
   */
  checkSlugAvailability(slug: string, domainId: string, admin = false): Observable<void> {
    const params = new HttpParams().set('domainId', domainId);

    return this.http.get<void>(`${admin ? this.baseUrlAdmin : this.baseUrl}/slug/${encodeURIComponent(slug)}`, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Create a new permalink
   */
  createPermalink(permalink: PermalinkCreator, admin = false): Observable<Permalink> {
    return this.http.post<Permalink>(admin ? this.baseUrlAdmin : this.baseUrl, permalink)
      .pipe(
        catchError(this.handleError)
      );
  }



  /**
   * Update an existing permalink
   */
  updatePermalink(permalink: PermalinkCreator, admin = false): Observable<Permalink> {
    return this.http.put<Permalink>(admin ? this.baseUrlAdmin : this.baseUrl, permalink)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Delete a permalink by ID
   */
  deletePermalinkById(id: string, admin = false): Observable<void> {
    return this.http.delete<void>(`${admin ? this.baseUrlAdmin : this.baseUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Delete a permalink by slug and domain
   */
  deletePermalinkBySlug(slug: string, domainId: string, admin = false): Observable<void> {
    const params = new HttpParams().set('domainId', domainId);

    return this.http.delete<void>(`${admin ? this.baseUrlAdmin : this.baseUrl}/slug/${encodeURIComponent(slug)}`, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Build HTTP parameters for paginated requests
   */
  private buildPageParams(domainId: string | null, pageRequest: Partial<PageRequest>): HttpParams {
    let params = new HttpParams()
    if (domainId) {
      params = params.set('domainId', domainId);
    }
    if (pageRequest.limit !== undefined) {
      params = params.set('limit', pageRequest.limit);
    }

    if (pageRequest.offset !== undefined) {
      params = params.set('offset', pageRequest.offset);
    }

    if (pageRequest.filter) {
      params = params.set('filter', pageRequest.filter);
    }

    if (pageRequest.sort) {
      params = params.set('sort_by', pageRequest.sort.property);
      params = params.set('sort_direction', pageRequest.sort.direction);
    }

    return params;
  }

  /**
   * Handle HTTP errors
   */
  private handleError = (error: any): Observable<never> => {
    let errorMessage: ApiError;

    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = {
        message: error.error.message || 'An unexpected error occurred',
        code: 'CLIENT_ERROR'
      };
    } else {
      // Server-side error
      errorMessage = {
        message: error.error?.message || `HTTP ${error.status}: ${error.statusText}`,
        code: error.error?.code || `HTTP_${error.status}`,
        timestamp: new Date().toISOString()
      };
    }

    console.error('PermalinkService Error:', errorMessage);
    return throwError(() => errorMessage);
  };

  /**
   * Utility methods for building requests
   */
  createPageRequest(
    limit: number = 20,
    offset: number = 0,
    filter?: string,
    sortProperty: SortProperty = SortProperty.CREATION_DATE,
    sortDirection: SortDirection = SortDirection.DESC
  ): PageRequest {
    return {
      limit: Math.min(limit, 50), // Cap at 50 as per backend logic
      offset: Math.max(offset, 0),
      filter,
      sort: {
        property: sortProperty,
        direction: sortDirection
      }
    };
  }
}
