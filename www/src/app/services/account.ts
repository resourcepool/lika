import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { Account, AccountCreator, ApiError } from '../models/permalink.model';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/api/admin/accounts`;

  /**
   * Get all accounts (admin endpoint)
   */
  getAllAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>(this.baseUrl)
       .pipe(
         catchError(this.handleError));
  }

  /**
   * Get account by ID
   */
  getAccountById(id: string): Observable<Account | null> {
    return this.http.get<Account | null>(`${this.baseUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Create a new account
   */
  createAccount(account: AccountCreator): Observable<Account> {
    return this.http.post<Account>(this.baseUrl, account)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Delete an account
   */
  deleteAccount(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`)
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Regenerate access token for an account
   */
  regenerateAccessToken(id: string): Observable<Account> {
    return this.http.post<Account>(`${this.baseUrl}/${id}/regenerate-access-token`, {})
      .pipe(
        catchError(this.handleError)
      );
  }

  /**
   * Regenerate public read token for an account
   */
  regeneratePublicReadToken(id: string): Observable<Account> {
    return this.http.post<Account>(`${this.baseUrl}/${id}/regenerate-public-read-token`, {})
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

    console.error('AccountService Error:', errorMessage);
    return throwError(() => errorMessage);
  };
}
