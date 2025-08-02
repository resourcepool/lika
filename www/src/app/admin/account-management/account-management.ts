import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatDivider } from '@angular/material/divider';
import { Footer } from '../../footer/footer';
import { Account, AccountCreator, ApiError } from '../../models/permalink.model';
import { AccountService } from '../../services/account';
import { AuthService } from '../../services/auth';

@Component({
  selector: 'app-account-management',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatMenuModule,
    Footer,
    MatDivider
  ],
  templateUrl: './account-management.html',
  styleUrl: './account-management.scss'
})
export class AccountManagementComponent implements OnInit {
  private accountService = inject(AccountService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);
  private fb = inject(FormBuilder);
  private dialog = inject(MatDialog);

  // Data properties
  protected accounts: Account[] = [];
  protected loading = false;
  protected showCreateForm = false;

  // Form
  protected accountForm: FormGroup;

  // Table configuration
  protected displayedColumns: string[] = ['name', 'global', 'defaultAccessToken', 'defaultPublicReadAccessToken', 'actions'];

  constructor() {
    this.accountForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      global: [false]
    });
  }

  ngOnInit() {
    this.loadAccounts();
  }

  private loadAccounts() {
    this.loading = true;
    this.accountService.getAllAccounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        this.loading = false;
      },
      error: (error: ApiError) => {
        console.error('Failed to load accounts:', error);
        this.snackBar.open(`Failed to load accounts: ${error.message}`, 'Close', {
          duration: 5000
        });
        this.loading = false;
      }
    });
  }

  toggleCreateForm() {
    this.showCreateForm = !this.showCreateForm;
    if (!this.showCreateForm) {
      this.accountForm.reset();
    }
  }

  onSubmit() {
    if (this.accountForm.valid) {
      const accountData: AccountCreator = this.accountForm.value;
      
      // TODO: Implement account creation when API service is available
      this.snackBar.open('Account creation will be implemented when API service is available', 'Close', {
        duration: 3000
      });
      
      // Mock success for now
      setTimeout(() => {
        this.snackBar.open('Account created successfully', 'Close', {
          duration: 3000
        });
        this.loadAccounts();
        this.toggleCreateForm();
      }, 1000);
    }
  }

  deleteAccount(account: Account) {
    if (confirm(`Are you sure you want to delete account "${account.name}"?`)) {
      // TODO: Implement account deletion when API service is available
      this.snackBar.open('Account deletion will be implemented when API service is available', 'Close', {
        duration: 3000
      });
    }
  }

  copyToClipboard(text: string) {
    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open('Token copied to clipboard!', 'Close', {
        duration: 2000
      });
    }).catch(() => {
      this.snackBar.open('Failed to copy to clipboard', 'Close', {
        duration: 3000
      });
    });
  }

  refreshData() {
    this.loadAccounts();
  }

  logout() {
    this.authService.logout();
  }

  maskToken(token: string): string {
    if (token.length <= 8) return token;
    return token.substring(0, 8) + '...' + token.substring(token.length - 4);
  }

  regenerateToken(account: Account, tokenType: 'access' | 'publicRead') {
    const tokenName = tokenType === 'access' ? 'access token' : 'public read token';
    if (confirm(`Are you sure you want to regenerate the ${tokenName} for "${account.name}"? This will invalidate the current token.`)) {
      // TODO: Implement token regeneration when API service is available
      this.snackBar.open(`${tokenName} regeneration will be implemented when API service is available`, 'Close', {
        duration: 3000
      });
    }
  }
}