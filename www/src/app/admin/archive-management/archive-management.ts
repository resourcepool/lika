import { Component, inject, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDivider } from '@angular/material/divider';
import { DatabaseArchive, ApiError } from '../../models/permalink.model';
import { ArchiveService } from '../../services/archive';
import { AuthService } from '../../services/auth';

@Component({
  selector: 'app-archive-management',
  imports: [
    CommonModule,
    RouterModule,
    MatToolbarModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDivider
  ],
  templateUrl: './archive-management.html',
  styleUrl: './archive-management.scss'
})
export class ArchiveManagementComponent {
  private archiveService = inject(ArchiveService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  protected loading = false;
  protected selectedFile: File | null = null;

  /**
   * Handle file selection for import
   */
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const file = input.files[0];

      // Validate file type
      if (file.type !== 'application/json' && !file.name.endsWith('.json')) {
        this.snackBar.open('Please select a JSON file', 'Close', {
          duration: 3000
        });
        this.clearFileInput();
        return;
      }

      this.selectedFile = file;
      this.snackBar.open(`File selected: ${file.name}`, 'Close', {
        duration: 2000
      });
    }
  }

  /**
   * Clear the file input
   */
  clearFileInput() {
    this.selectedFile = null;
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }

  /**
   * Import archive from selected file
   */
  importArchive() {
    if (!this.selectedFile) {
      this.snackBar.open('Please select a file first', 'Close', {
        duration: 3000
      });
      return;
    }

    this.loading = true;
    const reader = new FileReader();

    reader.onload = (e) => {
      try {
        const archiveData = JSON.parse(e.target?.result as string) as DatabaseArchive;
        console.log(archiveData);
        // Validate the archive structure
        if (!this.validateArchiveStructure(archiveData)) {
          this.snackBar.open('Invalid archive file format', 'Close', {
            duration: 5000
          });
          this.loading = false;
          return;
        }

        // Perform the import
        this.archiveService.importArchive(archiveData).subscribe({
          next: () => {
            this.snackBar.open('Archive imported successfully', 'Close', {
              duration: 3000
            });
            this.clearFileInput();
            this.loading = false;
          },
          error: (error: ApiError) => {
            console.error('Import failed:', error);
            this.snackBar.open(`Import failed: ${error.message}`, 'Close', {
              duration: 5000
            });
            this.loading = false;
          }
        });
      } catch (parseError) {
        console.error('Failed to parse JSON:', parseError);
        this.snackBar.open('Failed to parse JSON file', 'Close', {
          duration: 5000
        });
        this.loading = false;
      }
    };

    reader.onerror = () => {
      this.snackBar.open('Failed to read file', 'Close', {
        duration: 5000
      });
      this.loading = false;
    };

    reader.readAsText(this.selectedFile);
  }

  /**
   * Export archive to JSON file
   */
  exportArchive() {
    this.loading = true;

    this.archiveService.exportArchive().subscribe({
      next: (archiveData: DatabaseArchive) => {
        // Create and download the file
        const jsonString = JSON.stringify(archiveData, null, 2);
        const blob = new Blob([jsonString], { type: 'application/json' });
        const url = window.URL.createObjectURL(blob);

        // Create download link
        const link = document.createElement('a');
        link.href = url;
        link.download = `lika-archive-${new Date().toISOString().split('T')[0]}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        // Clean up
        window.URL.revokeObjectURL(url);

        this.snackBar.open('Archive exported successfully', 'Close', {
          duration: 3000
        });
        this.loading = false;
      },
      error: (error: ApiError) => {
        console.error('Export failed:', error);
        this.snackBar.open(`Export failed: ${error.message}`, 'Close', {
          duration: 5000
        });
        this.loading = false;
      }
    });
  }

  /**
   * Validate archive structure
   */
  private validateArchiveStructure(archive: any): archive is DatabaseArchive {
    console.log("1");
    if (!archive || typeof archive !== 'object') {
      return false;
    }
    console.log("2");
    if (!Array.isArray(archive.accounts)) {
      return false;
    }
    console.log("3");
    archive.accounts.forEach((account: any) => {
      console.log(typeof account === 'object' &&
        typeof account.name === 'string' &&
        typeof account.root === 'boolean' &&
        typeof account.global === 'boolean' &&
        Array.isArray(account.domains))
    })
    // Basic validation of account structure
    return archive.accounts.every((account: any) =>
      typeof account === 'object' &&
      typeof account.name === 'string' &&
      typeof account.root === 'boolean' &&
      typeof account.global === 'boolean' &&
      Array.isArray(account.domains)
    );
  }

  /**
   * Log out the current user
   */
  logout() {
    this.authService.logout();
  }
}
