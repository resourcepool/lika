import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private router = inject(Router);
  private readonly TOKEN_KEY = 'links_access_token';
  
  // Observable to track authentication state
  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  /**
   * Check if user has a stored token
   */
  hasToken(): boolean {
    const token = this.getToken();
    return !!token && token.length > 0;
  }

  /**
   * Get the stored access token
   */
  getToken(): string | null {
    try {
      return localStorage.getItem(this.TOKEN_KEY);
    } catch (error) {
      console.warn('Failed to access localStorage:', error);
      return null;
    }
  }

  /**
   * Store the access token
   */
  setToken(token: string): void {
    try {
      localStorage.setItem(this.TOKEN_KEY, token);
      this.isAuthenticatedSubject.next(true);
    } catch (error) {
      console.error('Failed to store token in localStorage:', error);
      throw error;
    }
  }

  /**
   * Remove the access token and redirect to sign-in
   */
  logout(): void {
    try {
      localStorage.removeItem(this.TOKEN_KEY);
      this.isAuthenticatedSubject.next(false);
      this.router.navigate(['/signin']);
    } catch (error) {
      console.error('Failed to remove token from localStorage:', error);
      // Still navigate to sign-in even if localStorage fails
      this.isAuthenticatedSubject.next(false);
      this.router.navigate(['/signin']);
    }
  }

  /**
   * Navigate to sign-in page if not authenticated
   */
  redirectToSignIn(): void {
    this.router.navigate(['/signin']);
  }

  /**
   * Navigate to dashboard after successful authentication
   */
  redirectToDashboard(): void {
    this.router.navigate(['/dashboard']);
  }

  /**
   * Validate token format (basic validation)
   */
  isValidTokenFormat(token: string): boolean {
    if (!token || token.length < 10) {
      return false;
    }
    
    // Basic JWT format check (has 3 parts separated by dots)
    const parts = token.split('.');
    if (parts.length === 3) {
      return true;
    }

    // For non-JWT tokens, just check length and characters
    return /^[A-Za-z0-9\-_]+$/.test(token);
  }

  /**
   * Initialize authentication check
   */
  initializeAuth(): void {
    const hasValidToken = this.hasToken();
    this.isAuthenticatedSubject.next(hasValidToken);
    
    if (!hasValidToken) {
      this.redirectToSignIn();
    }
  }
}
