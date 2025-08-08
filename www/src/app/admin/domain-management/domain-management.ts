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
import { Domain, AdminDomainCreator, Account, ApiError } from '../../models/permalink.model';
import { DomainService } from '../../services/domain';
import { AccountService } from '../../services/account';
import { AuthService } from '../../services/auth';
import { DomainCreationModalComponent } from '../domain-creation-modal/domain-creation-modal';

@Component({
  selector: 'app-domain-management',
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
  templateUrl: './domain-management.html',
  styleUrl: './domain-management.scss'
})
export class DomainManagementComponent implements OnInit {
  private domainService = inject(DomainService);
  private accountService = inject(AccountService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);
  private dialog = inject(MatDialog);

  // Data properties
  protected domains: Domain[] = [];
  protected accounts: Account[] = [];
  protected loading = false;

  // Table configuration
  protected displayedColumns: string[] = ['host', 'standard', 'ownerId', 'apiBaseUrl', 'landingBaseUrl', 'actions'];

  ngOnInit() {
    this.loadDomains();
    this.loadAccounts();
  }

  private loadDomains() {
    this.loading = true;
    this.domainService.getAllDomainsAdmin().subscribe({
      next: (domains) => {
        this.domains = domains;
        this.loading = false;
      },
      error: (error: ApiError) => {
        console.error('Failed to load domains:', error);
        this.snackBar.open(`Failed to load domains: ${error.message}`, 'Close', {
          duration: 5000
        });
        this.loading = false;
        // Fallback to mock data for development
        this.domains = [
          { id: '1', host: 'short.ly', standard: false, ownerId: 'user1', apiBaseUrl: 'https://api.short.ly', landingBaseUrl: 'https://landing.short.ly' },
          { id: '2', host: 'link.co', standard: true, ownerId: 'user2', apiBaseUrl: 'https://api.link.co', landingBaseUrl: 'https://landing.link.co' }
        ];
      }
    });
  }

  private loadAccounts() {
    this.accountService.getAllAccounts().subscribe({
      next: (accounts) => {
        this.accounts = accounts;
      },
      error: (error: ApiError) => {
        console.error('Failed to load accounts:', error);
        this.snackBar.open(`Failed to load accounts: ${error.message}`, 'Close', {
          duration: 5000
        });
        // Fallback to empty array
        this.accounts = [];
      }
    });
  }

  openCreateDomainModal() {
    const dialogRef = this.dialog.open(DomainCreationModalComponent, {
      width: '600px',
      data: { accounts: this.accounts }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        console.log('The dialog was closed', result);
        this.createDomain(result);
      }
    });
  }

  private createDomain(domainData: AdminDomainCreator) {
    this.domainService.createAdminDomain(domainData).subscribe({
      next: (domain) => {
        this.snackBar.open('Domain created successfully', 'Close', {
          duration: 3000
        });
        this.loadDomains();
      },
      error: (error: ApiError) => {
        this.snackBar.open(`Failed to create domain: ${error.message}`, 'Close', {
          duration: 5000
        });
      }
    });
  }

  deleteDomain(domain: Domain) {
    if (confirm(`Are you sure you want to delete domain ${domain.host}?`)) {
      if (!domain.id) return;

      this.domainService.deleteAdminDomain(domain.id).subscribe({
        next: () => {
          this.snackBar.open('Domain deleted successfully', 'Close', {
            duration: 3000
          });
          this.loadDomains();
        },
        error: (error: ApiError) => {
          this.snackBar.open(`Failed to delete domain: ${error.message}`, 'Close', {
            duration: 5000
          });
        }
      });
    }
  }

  refreshData() {
    this.loadDomains();
  }

  logout() {
    this.authService.logout();
  }

  formatUrl(url?: string): string {
    if (!url) return '-';
    return url.length > 30 ? url.substring(0, 30) + '...' : url;
  }

  getAccountName(ownerId?: string): string {
    if (!ownerId) return 'System';
    const account = this.accounts.find(acc => acc.id === ownerId);
    return account ? account.name : `Account ${ownerId}`;
  }
}
