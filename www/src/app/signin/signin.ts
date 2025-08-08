import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../services/auth';
import { CommonModule } from '@angular/common';
import {AccountService} from '../services/account';

@Component({
  selector: 'app-signin',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  templateUrl: './signin.html',
  styleUrl: './signin.scss'
})
export class SigninComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);
  private accountService = inject(AccountService);

  signinForm: FormGroup;
  hideToken = true;
  isLoading = false;

  constructor() {
    this.signinForm = this.fb.group({
      accessToken: ['', [Validators.required, Validators.minLength(10)]]
    });
  }

  async onSubmit() {
    if (this.signinForm.valid) {
      const token = this.signinForm.get('accessToken')?.value;

      if (!this.authService.isValidTokenFormat(token)) {
        this.snackBar.open('Invalid token format. Please check your access token.', 'Close', {
          duration: 5000
        });
        return;
      }

      let isAdmin = false;
      try {
         await this.accountService.getAllAccounts();
         isAdmin = true;
      } catch (error) {

      }

      this.isLoading = true;

      try {
        this.authService.setToken(token, isAdmin);
        this.snackBar.open('Successfully authenticated!', 'Close', {
          duration: 3000
        });
        this.authService.redirectToDashboard();
      } catch (error) {
        console.error('Failed to store token:', error);
        this.snackBar.open('Failed to store access token. Please try again.', 'Close', {
          duration: 5000
        });
      } finally {
        this.isLoading = false;
      }
    } else {
      this.markFormGroupTouched();
    }
  }

  toggleTokenVisibility() {
    this.hideToken = !this.hideToken;
  }

  private markFormGroupTouched() {
    Object.keys(this.signinForm.controls).forEach(key => {
      const control = this.signinForm.get(key);
      control?.markAsTouched();
    });
  }

  getTokenErrorMessage() {
    const control = this.signinForm.get('accessToken');
    if (control?.hasError('required')) {
      return 'Access token is required';
    }
    if (control?.hasError('minLength')) {
      return 'Access token must be at least 10 characters long';
    }
    return '';
  }
}
