import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { Permalink, ApiError } from '../models/permalink.model';

@Injectable({
  providedIn: 'root'
})
export class SearchService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/search`;

  /**
   * Get slug suggestions based on search string
   */
  getSlugSuggestions(searchStr: string, domainId: string): Observable<string[]> {
    const params = new HttpParams().set('domainId', domainId);
    
    return this.http.get<string[]>(`${this.baseUrl}/slug/suggestions/${encodeURIComponent(searchStr)}`, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Search for a specific permalink by slug
   */
  searchBySlug(slug: string, domainId?: string, host?: string): Observable<Permalink> {
    let params = new HttpParams();
    let headers = new HttpHeaders();

    if (domainId) {
      params = params.set('domainId', domainId);
    }

    if (host) {
      headers = headers.set('Host', host);
    }

    return this.http.get<Permalink>(`${this.baseUrl}/slug/${encodeURIComponent(slug)}`, { 
      params, 
      headers 
    })
      .pipe(
        catchError(this.handleError)
      );
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

    console.error('SearchService Error:', errorMessage);
    return throwError(() => errorMessage);
  };
}
