import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Skip authentication for public endpoints
  const publicEndpoints = ['/public'];
  const isPublicEndpoint = publicEndpoints.some(endpoint => req.url.includes(endpoint));

  if (isPublicEndpoint) {
    return next(req);
  }

  // Get the token from the auth service
  const token = authService.getToken();

  // Clone the request and add the Authorization header if token exists
  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  // Handle the request and catch authentication errors
  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If we get a 401 Unauthorized, redirect to sign-in
      if (error.status === 401) {
        console.warn('Authentication failed, redirecting to sign-in');
        authService.logout();
        return throwError(() => new Error('Authentication failed'));
      }
      
      // If we get a 403 Forbidden, the token might be invalid
      if (error.status === 403) {
        console.warn('Access forbidden, token might be invalid');
        // Optionally redirect to sign-in for 403 as well
        // authService.logout();
      }

      return throwError(() => error);
    })
  );
};
