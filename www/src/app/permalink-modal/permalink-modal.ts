import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { CommonModule } from '@angular/common';
import { PermalinkCreator, PermalinkType, Domain, OptionsDto } from '../models/permalink.model';

@Component({
  selector: 'app-permalink-modal',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatCheckboxModule
  ],
  templateUrl: './permalink-modal.html',
  styleUrl: './permalink-modal.scss'
})
export class PermalinkModal implements OnInit {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<PermalinkModal>);
  private data = inject<{ domains: Domain[], permalink?: PermalinkCreator }>(MAT_DIALOG_DATA);

  permalinkForm!: FormGroup;
  domains: Domain[] = [];
  permalinkTypes = Object.values(PermalinkType);
  isEditMode = false;

  ngOnInit() {
    this.domains = this.data?.domains || [];
    this.isEditMode = !!this.data?.permalink;

    this.initializeForm();

    if (this.isEditMode && this.data.permalink) {
      // Ensure options structure exists for editing
      const permalinkData = {
        ...this.data.permalink,
        options: {
          activation: this.data.permalink.options?.activation || { activationInstant: null, targetUrlBeforeActivation: '' },
          landing: this.data.permalink.options?.landing || { templateId: '' },
          expiry: this.data.permalink.options?.expiry || { expiryDate: null }
        }
      };
      this.permalinkForm.patchValue(permalinkData);
    }
  }

  private initializeForm() {
    this.permalinkForm = this.fb.group({
      id: [null],
      targetUrl: ['', [Validators.required, Validators.pattern(/^https?:\/\/.+/)]],
      domainId: ['', Validators.required],
      slug: [''],
      type: [PermalinkType.SIMPLE],
      // Options group
      options: this.fb.group({
        activation: this.fb.group({
          activationInstant: [null],
          targetUrlBeforeActivation: ['']
        }),
        landing: this.fb.group({
          templateId: ['']
        }),
        expiry: this.fb.group({
          expiryDate: [null]
        })
      })
    });

    // Watch for type changes to show/hide landing options
    this.permalinkForm.get('type')?.valueChanges.subscribe(type => {
      const landingGroup = this.permalinkForm.get('options.landing');
      if (type === PermalinkType.WITH_LANDING) {
        landingGroup?.get('templateId')?.setValidators([Validators.required]);
      } else {
        landingGroup?.get('templateId')?.clearValidators();
        landingGroup?.get('templateId')?.setValue('');
      }
      landingGroup?.get('templateId')?.updateValueAndValidity();
    });
  }

  onSubmit() {
    if (this.permalinkForm.valid) {
      const formValue = this.permalinkForm.value;

      // Convert dates to ISO strings if they exist
      if (formValue.options?.activation?.activationInstant) {
        formValue.options.activation.activationInstant = new Date(formValue.options.activation.activationInstant).toISOString();
      }
      if (formValue.options?.expiry?.expiryDate) {
        formValue.options.expiry.expiryDate = new Date(formValue.options.expiry.expiryDate).toLocaleDateString('en-CA'); // YYYY-MM-DD format
      }

      // Clean up empty options
      this.cleanupEmptyOptions(formValue.options);

      this.dialogRef.close(formValue);
    }
  }

  private cleanupEmptyOptions(options: OptionsDto) {
    // Remove empty activation options
    if (options.activation && !options.activation.activationInstant && !options.activation.targetUrlBeforeActivation) {
      delete options.activation;
    }

    // Remove empty landing options
    if (options.landing && !options.landing.templateId) {
      delete options.landing;
    }

    // Remove empty expiry options
    if (options.expiry && !options.expiry.expiryDate) {
      delete options.expiry;
    }
  }

  onCancel() {
    this.dialogRef.close();
  }

  get isWithLanding() {
    return this.permalinkForm.get('type')?.value === PermalinkType.WITH_LANDING;
  }

  get hasActivationOptions() {
    const activation = this.permalinkForm.get('options.activation');
    return activation?.get('activationInstant')?.value || activation?.get('targetUrlBeforeActivation')?.value;
  }
}
