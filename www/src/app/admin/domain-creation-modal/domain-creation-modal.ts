import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CommonModule } from '@angular/common';
import { AdminDomainCreator, Account } from '../../models/permalink.model';

@Component({
  selector: 'app-domain-creation-modal',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './domain-creation-modal.html',
  styleUrl: './domain-creation-modal.scss'
})
export class DomainCreationModalComponent implements OnInit {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<DomainCreationModalComponent>);
  private data = inject<{ accounts: Account[] }>(MAT_DIALOG_DATA);

  domainForm!: FormGroup;
  accounts: Account[] = [];
  loading = false;

  ngOnInit() {
    this.accounts = this.data?.accounts || [];
    this.initializeForm();
  }

  private initializeForm() {
    this.domainForm = this.fb.group({
      host: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/)]],
      targetOwnerId: [''],
      apiBaseUrl: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
      landingBaseUrl: ['', Validators.pattern(/^https?:\/\/.+/)]
    });
  }

  onSubmit() {
    if (this.domainForm.valid) {
      const formValue = this.domainForm.value;
      
      // Clean up empty values
      const domainData: AdminDomainCreator = {
        host: formValue.host,
        apiBaseUrl: formValue.apiBaseUrl,
        landingBaseUrl: formValue.landingBaseUrl || '',
        targetOwnerId: formValue.targetOwnerId || undefined
      };

      this.dialogRef.close(domainData);
    }
  }

  onCancel() {
    this.dialogRef.close();
  }

  getAccountDisplayName(account: Account): string {
    return `${account.name}${account.global ? ' (Global)' : ''}`;
  }
}