import { Component, signal, inject, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CommonModule } from '@angular/common';
import {debounceTime, distinctUntilChanged, startWith, combineLatest, map} from 'rxjs';
import { DeeplinkModal } from '../permalink-modal/permalink-modal';
import { Footer } from '../footer/footer';
import { Domain, Deeplink, DeeplinkCreator, ApiError, PageRequest, PageResponse, SortDirection, SortProperty } from '../models/permalink.model';
import { MatDivider } from '@angular/material/divider';
import { DomainService } from '../services/domain';
import { DeeplinkService } from '../services/permalink';
import { AuthService } from '../services/auth';

@Component({
  selector: 'app-dashboard',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatIconModule,
    MatMenuModule,
    MatTableModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    Footer,
    MatDivider
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class DashboardComponent implements OnInit {
  protected readonly title = signal('Links Dashboard');
  private dialog = inject(MatDialog);
  private domainService = inject(DomainService);
  private deeplinkService = inject(DeeplinkService);
  private snackBar = inject(MatSnackBar);
  private authService = inject(AuthService);
  private router = inject(Router);

  // Data properties
  protected domains: Domain[] = [];
  protected displayedDeeplinks: Deeplink[] = []; // Current page deeplinks from server
  protected loading = false;
  protected loadingDeeplinks = false;
  protected totalDeeplinks = 0;
  protected isAdmin = false;

  // Pagination properties
  protected pageSize = 50;
  protected pageSizeOptions = [5, 10, 20, 50];
  protected currentPage = 0;

  // Current domain being viewed (for multi-domain pagination)
  protected selectedDomainId: string | null = null;

  // Filter controls
  protected searchControl = new FormControl('');
  protected domainFilterControl = new FormControl<string | null>(null);

  // Table configuration
  protected displayedColumns: string[] = ['shortenedUrl', 'targetUrl', 'status', 'createdAt', 'actions'];

  ngOnInit() {
    this.isAdmin = this.authService.isAdmin();
    this.selectedDomainId = this.authService.getFavoriteDomain();
    if (this.selectedDomainId) {
      this.domainFilterControl.setValue(this.selectedDomainId);
    }
    this.loadDomains();
    this.setupFilters();
  }

  private loadDomains() {
    this.loading = true;
    this.domainService.getAllDomains(this.isAdmin).subscribe({
      next: (domains) => {
        this.domains = domains;
        this.loading = false;
        // Load deeplinks with pagination
        this.loadDeeplinks();
      },
      error: (error: ApiError) => {
        console.error('Failed to load domains:', error);
        this.snackBar.open(`Failed to load domains: ${error.message}`, 'Close', {
          duration: 5000
        });
        this.loading = false;
        // Fallback to mock data for development
        this.domains = [
          { id: '1', host: 'short.ly', standard: false, apiBaseUrl: '', landingBaseUrl: '' },
          { id: '2', host: 'link.co', standard: false, apiBaseUrl: '', landingBaseUrl: '' }
        ];
        this.loadDeeplinks();
      }
    });
  }

  private loadDeeplinks() {
    if (this.domains.length === 0) return;

    this.loadingDeeplinks = true;
    this.displayedDeeplinks = [];

    // Build page request
    const searchTerm = (this.searchControl.value || '').toLowerCase().trim();
    const pageRequest = this.deeplinkService.createPageRequest(
      this.pageSize,
      this.currentPage * this.pageSize,
      searchTerm || undefined,
      SortProperty.CREATION_DATE,
      SortDirection.DESC
    );

    if (this.selectedDomainId) {
      // Load from specific domain
      this.loadFromSingleDomain(this.selectedDomainId, pageRequest);
    } else {

    }
  }

  private loadFromSingleDomain(domainId: string | null, pageRequest: PageRequest) {
    this.deeplinkService.getDeeplinksPage(domainId, pageRequest, this.isAdmin).subscribe({
      next: (response: PageResponse<Deeplink>) => {
        // Find domain info for enriching deeplinks
        const domain = this.domains.find(d => d.id === domainId);

        // Add domain info to each deeplink
        this.displayedDeeplinks = response.items.map(p => ({
          ...p,
          domainHost: domain?.host || 'Unknown',
          domainId: domain?.id || 'Unknown',
          createdAt: p.createdDateTime || p.createdAt,
          options: p.options || {}
        }));

        this.totalDeeplinks = response.totalItemCount;
        this.loadingDeeplinks = false;
      },
      error: (error: ApiError) => {
        console.error(`Failed to load deeplinks for domain ${domainId}:`, error);
        this.snackBar.open(`Failed to load deeplinks: ${error.message}`, 'Close', {
          duration: 5000
        });
        this.loadingDeeplinks = false;
      }
    });
  }

  private setupFilters() {
    // Combine search and domain filter changes
    combineLatest([
      this.searchControl.valueChanges.pipe(
        startWith(''),
        map(value => value?.toLowerCase().trim() || ''),
        debounceTime(300),
        distinctUntilChanged()
      ),
      this.domainFilterControl.valueChanges
    ]).subscribe(() => {
      this.applyFilters();
      this.resetPagination();
    });

  }

  private applyFilters() {
    // Handle domain selection
    const selectedDomain = this.domainFilterControl.value;

    if (selectedDomain) {
      this.selectedDomainId = selectedDomain;
      this.authService.setFavoriteDomain(selectedDomain);
    } else {
      // For 'all' domains, we'll use the first domain for now
      // In a real implementation, you might want to load from all domains
      this.selectedDomainId = null;
    }

    // Load deeplinks with new filters
    this.loadDeeplinks();
  }

  openCreateDeeplinkModal() {
    if (this.domains.length === 0) {
      this.snackBar.open('No domains available. Please add a domain first.', 'Close', {
        duration: 3000
      });
      return;
    }

    const dialogRef = this.dialog.open(DeeplinkModal, {
      width: '600px',
      data: { domains: this.domains }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.createDeeplink(result);
      }
    });
  }

  private createDeeplink(deeplinkData: DeeplinkCreator) {
    this.deeplinkService.createDeeplink(deeplinkData, this.isAdmin).subscribe({
      next: (deeplink) => {
        this.snackBar.open(`Deeplink created successfully: ${deeplink.shortenedUrl}`, 'Close', {
          duration: 5000
        });
        console.log('Deeplink created:', deeplink);
        // Refresh the deeplink list
        this.loadDeeplinks();
      },
      error: (error: ApiError) => {
        this.snackBar.open(`Failed to create deeplink: ${error.message}`, 'Close', {
          duration: 5000
        });
        console.error('Failed to create deeplink:', error);
      }
    });
  }

  openManageDomains() {
    this.router.navigate(['/admin/domains']);
  }

  logout() {
    this.authService.logout();
  }

  // Table utility methods
  copyToClipboard(text: string) {
    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open('Copied to clipboard!', 'Close', {
        duration: 2000
      });
    }).catch(() => {
      this.snackBar.open('Failed to copy to clipboard', 'Close', {
        duration: 3000
      });
    });
  }

  openUrl(url: string) {
    window.open(url, '_blank');
  }

  deleteDeeplink(deeplink: Deeplink) {
    if (confirm(`Are you sure you want to delete ${deeplink.shortenedUrl}?`)) {
      this.deeplinkService.deleteDeeplinkById(deeplink.id, this.isAdmin).subscribe({
        next: () => {
          this.snackBar.open('Deeplink deleted successfully', 'Close', {
            duration: 3000
          });
          this.loadDeeplinks();
        },
        error: (error: ApiError) => {
          this.snackBar.open(`Failed to delete deeplink: ${error.message}`, 'Close', {
            duration: 5000
          });
        }
      });
    }
  }


  formatDate(date: string | Date): string {
    const d = new Date(date);
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString();
  }

  refreshData() {
    this.resetPagination();
  }

  // Options utility methods
  getDeeplinkStatus(deeplink: Deeplink): { status: string; color: string; icon: string } {
    const now = new Date();

    // Check if it has activation options
    if (deeplink.options?.activation?.activationInstant) {
      const activationDate = new Date(deeplink.options.activation.activationInstant);
      if (now < activationDate) {
        return { status: 'Scheduled', color: 'warn', icon: 'schedule' };
      }
    }

    // Check if it's expired
    if (deeplink.options?.expiry?.expiryDate) {
      const expiryDate = new Date(deeplink.options.expiry.expiryDate);
      if (now > expiryDate) {
        return { status: 'Expired', color: 'warn', icon: 'expired' };
      }
    }

    return { status: 'Active', color: 'primary', icon: 'check_circle' };
  }

  hasLandingPage(deeplink: Deeplink): boolean {
    return deeplink.type === 'WITH_LANDING' && !!deeplink.options?.landing?.templateId;
  }

  getOptionsTooltip(deeplink: Deeplink): string {
    const options: string[] = [];

    if (deeplink.options?.activation?.activationInstant) {
      const date = new Date(deeplink.options.activation.activationInstant);
      options.push(`Activates: ${date.toLocaleDateString()} ${date.toLocaleTimeString()}`);
    }

    if (deeplink.options?.activation?.targetUrlBeforeActivation) {
      options.push(`Before activation: ${deeplink.options.activation.targetUrlBeforeActivation}`);
    }

    if (deeplink.options?.expiry?.expiryDate) {
      const date = new Date(deeplink.options.expiry.expiryDate);
      options.push(`Expires: ${date.toLocaleDateString()}`);
    }

    if (deeplink.options?.landing?.templateId) {
      options.push(`Landing template: ${deeplink.options.landing.templateId}`);
    }

    return options.length > 0 ? options.join('\n') : 'No special options';
  }

  // Pagination methods
  onPageChange(event: PageEvent) {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadDeeplinks(); // Load new page from server
  }

  resetPagination() {
    this.currentPage = 0;
    this.loadDeeplinks();
  }

  // Helper method to get domain name by ID
  getDomainName(domainId: string): string {
    return this.domains.find(d => d.id === domainId)?.host || 'Unknown';
  }

  onDomainChange(selected: string) {
    this.domainFilterControl.setValue(selected);
  }
}
