import { Component, signal, inject, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
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
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CommonModule } from '@angular/common';
import { debounceTime, distinctUntilChanged, startWith, combineLatest } from 'rxjs';
import { PermalinkModal } from '../permalink-modal/permalink-modal';
import { Footer } from '../footer/footer';
import { Domain, Permalink, PermalinkCreator, ApiError } from '../models/permalink.model';
import { MatDivider } from '@angular/material/divider';
import { DomainService } from '../services/domain';
import { PermalinkService } from '../services/permalink';
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
  private permalinkService = inject(PermalinkService);
  private snackBar = inject(MatSnackBar);
  private authService = inject(AuthService);

  // Data properties
  protected domains: Domain[] = [];
  protected permalinks: Permalink[] = [];
  protected filteredPermalinks: Permalink[] = [];
  protected loading = false;
  protected loadingPermalinks = false;

  // Filter controls
  protected searchControl = new FormControl('');
  protected domainFilterControl = new FormControl('all');

  // Table configuration
  protected displayedColumns: string[] = ['shortenedUrl', 'targetUrl', 'domain', 'status', 'createdAt', 'actions'];

  ngOnInit() {
    this.loadDomains();
    this.setupFilters();
  }

  private loadDomains() {
    this.loading = true;
    this.domainService.getAllDomains().subscribe({
      next: (domains) => {
        this.domains = domains;
        this.loading = false;
        // Load permalinks for all domains
        this.loadAllPermalinks();
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
        this.loadAllPermalinks();
      }
    });
  }

  private loadAllPermalinks() {
    if (this.domains.length === 0) return;

    this.loadingPermalinks = true;
    this.permalinks = [];

    // Load permalinks from all domains and combine them
    const domainRequests = this.domains.map(domain =>
      this.permalinkService.getAllPermalinks(domain.id!)
    );

    // Subscribe to all domain requests
    domainRequests.forEach((request, index) => {
      request.subscribe({
        next: (domainPermalinks) => {
          // Add domain info to each permalink
          const enrichedPermalinks = domainPermalinks.map(p => ({
            ...p,
            domainHost: this.domains[index].host,
            domainId: this.domains[index].id,
            createdAt: p.createdDateTime || p.createdAt, // Ensure createdAt is available
            options: p.options || {} // Ensure options object exists
          }));

          this.permalinks = [...this.permalinks, ...enrichedPermalinks];
          this.applyFilters();
        },
        error: (error: ApiError) => {
          console.error(`Failed to load permalinks for domain ${this.domains[index].host}:`, error);
        },
        complete: () => {
          // Check if all requests are done
          if (index === domainRequests.length - 1) {
            this.loadingPermalinks = false;
          }
        }
      });
    });
  }

  private setupFilters() {
    // Combine search and domain filter changes
    combineLatest([
      this.searchControl.valueChanges.pipe(
        startWith(''),
        debounceTime(300),
        distinctUntilChanged()
      ),
      this.domainFilterControl.valueChanges.pipe(
        startWith('all')
      )
    ]).subscribe(() => {
      this.applyFilters();
    });
  }

  private applyFilters() {
    let filtered = [...this.permalinks];
    const searchTerm = (this.searchControl.value || '').toLowerCase().trim();
    const selectedDomain = this.domainFilterControl.value;

    // Apply search filter
    if (searchTerm) {
      filtered = filtered.filter(permalink =>
        permalink.shortenedUrl.toLowerCase().includes(searchTerm) ||
        permalink.targetUrl.toLowerCase().includes(searchTerm) ||
        (permalink.slug && permalink.slug.toLowerCase().includes(searchTerm)) ||
        (permalink.domainHost && permalink.domainHost.toLowerCase().includes(searchTerm))
      );
    }

    // Apply domain filter
    if (selectedDomain && selectedDomain !== 'all') {
      filtered = filtered.filter(permalink => permalink.domainId === selectedDomain);
    }

    this.filteredPermalinks = filtered;
  }

  openCreatePermalinkModal() {
    if (this.domains.length === 0) {
      this.snackBar.open('No domains available. Please add a domain first.', 'Close', {
        duration: 3000
      });
      return;
    }

    const dialogRef = this.dialog.open(PermalinkModal, {
      width: '600px',
      data: { domains: this.domains }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.createPermalink(result);
      }
    });
  }

  private createPermalink(permalinkData: PermalinkCreator) {
    this.permalinkService.createPermalink(permalinkData).subscribe({
      next: (permalink) => {
        this.snackBar.open(`Permalink created successfully: ${permalink.shortenedUrl}`, 'Close', {
          duration: 5000
        });
        console.log('Permalink created:', permalink);
        // Refresh the permalink list
        this.loadAllPermalinks();
      },
      error: (error: ApiError) => {
        this.snackBar.open(`Failed to create permalink: ${error.message}`, 'Close', {
          duration: 5000
        });
        console.error('Failed to create permalink:', error);
      }
    });
  }

  openManageDomainsModal() {
    // TODO: Implement domain management modal
    this.snackBar.open('Domain management coming soon!', 'Close', {
      duration: 3000
    });
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

  deletePermalink(permalink: Permalink) {
    if (confirm(`Are you sure you want to delete ${permalink.shortenedUrl}?`)) {
      this.permalinkService.deletePermalinkById(permalink.id).subscribe({
        next: () => {
          this.snackBar.open('Permalink deleted successfully', 'Close', {
            duration: 3000
          });
          this.loadAllPermalinks();
        },
        error: (error: ApiError) => {
          this.snackBar.open(`Failed to delete permalink: ${error.message}`, 'Close', {
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
    this.loadAllPermalinks();
  }

  // Options utility methods
  getPermalinkStatus(permalink: Permalink): { status: string; color: string; icon: string } {
    const now = new Date();
    
    // Check if it has activation options
    if (permalink.options?.activation?.activationInstant) {
      const activationDate = new Date(permalink.options.activation.activationInstant);
      if (now < activationDate) {
        return { status: 'Scheduled', color: 'warn', icon: 'schedule' };
      }
    }
    
    // Check if it's expired
    if (permalink.options?.expiry?.expiryDate) {
      const expiryDate = new Date(permalink.options.expiry.expiryDate);
      if (now > expiryDate) {
        return { status: 'Expired', color: 'warn', icon: 'expired' };
      }
    }
    
    return { status: 'Active', color: 'primary', icon: 'check_circle' };
  }

  hasLandingPage(permalink: Permalink): boolean {
    return permalink.type === 'WITH_LANDING' && !!permalink.options?.landing?.templateId;
  }

  getOptionsTooltip(permalink: Permalink): string {
    const options: string[] = [];
    
    if (permalink.options?.activation?.activationInstant) {
      const date = new Date(permalink.options.activation.activationInstant);
      options.push(`Activates: ${date.toLocaleDateString()} ${date.toLocaleTimeString()}`);
    }
    
    if (permalink.options?.activation?.targetUrlBeforeActivation) {
      options.push(`Before activation: ${permalink.options.activation.targetUrlBeforeActivation}`);
    }
    
    if (permalink.options?.expiry?.expiryDate) {
      const date = new Date(permalink.options.expiry.expiryDate);
      options.push(`Expires: ${date.toLocaleDateString()}`);
    }
    
    if (permalink.options?.landing?.templateId) {
      options.push(`Landing template: ${permalink.options.landing.templateId}`);
    }
    
    return options.length > 0 ? options.join('\n') : 'No special options';
  }
}
