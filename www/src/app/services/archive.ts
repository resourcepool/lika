import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { DatabaseArchive, ApiError } from '../models/permalink.model';

@Injectable({
  providedIn: 'root'
})
export class ArchiveService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/admin/archive`;

  /**
   * Export archive data
   */
  exportArchive(): Observable<DatabaseArchive> {
    return this.http.post<DatabaseArchive>(`${this.baseUrl}/export`, {})
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Import archive data
   */
  importArchive(archive: DatabaseArchive): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/import`, archive)
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

    console.error('ArchiveService Error:', errorMessage);
    return throwError(() => errorMessage);
  };
}