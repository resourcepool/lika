import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { Domain, DomainCreator, AdminDomainCreator, ApiError } from '../models/permalink.model';

@Injectable({
  providedIn: 'root'
})
export class DomainService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/domains`;
  private readonly adminBaseUrl = `${environment.apiBaseUrl}/api/admin/domains`;

  /**
   * Get all domains for the current user
   */
  getAllDomains(): Observable<Domain[]> {
    return this.http.get<Domain[]>(this.baseUrl)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Admin: Get domain by ID
   */
  getDomainById(id: string): Observable<Domain | null> {
    return this.http.get<Domain | null>(`${this.adminBaseUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Admin: Get all domains (admin access)
   */
  getAllDomainsAdmin(): Observable<Domain[]> {
    return this.http.get<Domain[]>(this.adminBaseUrl)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Admin: Create a new domain
   */
  createDomain(domain: DomainCreator, ownerId?: string, global?: boolean): Observable<Domain> {
    let params = new HttpParams();
    console.log(domain);
    console.log(ownerId);
    if (ownerId) {
      params = params.set('ownerId', ownerId);
    }
    if (global) {
      params = params.set('global', 'true');
    }
    return this.http.post<Domain>(this.adminBaseUrl, domain, { params })
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Admin: Create a new domain with admin creator
   */
  createAdminDomain(domain: AdminDomainCreator): Observable<Domain> {
    console.log(domain);
    return this.createDomain({
      host: domain.host,
      apiBaseUrl: domain.apiBaseUrl,
      landingBaseUrl: domain.landingBaseUrl || ''
    }, domain.targetOwnerId, domain.global);
  }

  /**
   * Admin: Delete a domain
   */
  deleteDomain(id: string): Observable<void> {
    return this.http.delete<void>(`${this.adminBaseUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Admin: Delete a domain (alias for deleteDomain)
   */
  deleteAdminDomain(id: string): Observable<void> {
    return this.deleteDomain(id);
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

    console.error('DomainService Error:', errorMessage);
    return throwError(() => errorMessage);
  };
}
