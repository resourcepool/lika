import { Component, inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, AsyncValidatorFn, AbstractControl, ValidationErrors } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { CommonModule } from '@angular/common';
import { DeeplinkCreator, DeeplinkType, Domain, OptionsDto } from '../models/permalink.model';
import { AuthService } from '../services/auth';
import { DeeplinkService } from '../services/permalink';
import { Observable, of, timer } from 'rxjs';
import { map, catchError, switchMap, distinctUntilChanged, debounceTime } from 'rxjs/operators';

@Component({
  selector: 'app-deeplink-modal',
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
export class DeeplinkModal implements OnInit {
  private fb = inject(FormBuilder);
  private dialogRef = inject(MatDialogRef<DeeplinkModal>);
  private data = inject<{ domains: Domain[], deeplink?: DeeplinkCreator }>(MAT_DIALOG_DATA);
  private authService = inject(AuthService);
  private deeplinkService = inject(DeeplinkService);
  deeplinkForm!: FormGroup;
  domains: Domain[] = [];
  deeplinkTypes = Object.values(DeeplinkType);
  isEditMode = false;

  private slugAvailabilityValidator(): AsyncValidatorFn {
    return (control: AbstractControl): Observable<ValidationErrors | null> => {
      if (!control.value || control.value.trim() === '') {
        return of(null);
      }

      const domainId = this.deeplinkForm?.get('domainId')?.value;
      if (!domainId) {
        return of(null);
      }

      // Skip validation if in edit mode and the slug hasn't changed
      if (this.isEditMode && this.data?.deeplink?.slug === control.value) {
        return of(null);
      }

      return timer(300).pipe(
        switchMap(() => this.deeplinkService.checkSlugAvailability(control.value, domainId, this.authService.isAdmin())),
        map(() => null), // Success means slug is available
        catchError((error) => {
          // If error occurs, slug is not available
          return of({ slugUnavailable: true });
        })
      );
    };
  }

  ngOnInit() {
    this.domains = this.data?.domains || [];
    this.isEditMode = !!this.data?.deeplink;


    this.initializeForm();

    if (this.isEditMode && this.data.deeplink) {
      // Ensure options structure exists for editing and convert date strings to Date objects
      const deeplinkData = {
        ...this.data.deeplink,
        options: {
          activation: {
            activationInstant: this.data.deeplink.options?.activation?.activationInstant
              ? this.convertISOToDatetimeLocal(this.data.deeplink.options.activation.activationInstant)
              : null,
            targetUrlBeforeActivation: this.data.deeplink.options?.activation?.targetUrlBeforeActivation || ''
          },
          landing: this.data.deeplink.options?.landing || { templateId: '' },
          expiry: {
            expiryDate: this.data.deeplink.options?.expiry?.expiryDate
              ? new Date(this.data.deeplink.options.expiry.expiryDate)
              : null
          },
          password: this.data.deeplink.options?.password
        }
      };
      this.deeplinkForm.patchValue(deeplinkData);
    }
  }

  private initializeForm() {
    this.deeplinkForm = this.fb.group({
      id: [null],
      targetUrl: ['', [Validators.required, Validators.pattern(/^.+:\/\/.+/)]],
      domainId: [this.authService.getFavoriteDomain() || '', Validators.required],
      slug: ['', [], [this.slugAvailabilityValidator()]],
      type: [DeeplinkType.SIMPLE],
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
        }),
        password: this.fb.group({
          password: ['']
        })
      })
    });

    // Watch for type changes to show/hide landing options
    this.deeplinkForm.get('type')?.valueChanges.subscribe(type => {
      const landingGroup = this.deeplinkForm.get('options.landing');
      if (type === DeeplinkType.WITH_LANDING) {
        landingGroup?.get('templateId')?.setValidators([Validators.required]);
      } else {
        landingGroup?.get('templateId')?.clearValidators();
        landingGroup?.get('templateId')?.setValue('');
      }
      landingGroup?.get('templateId')?.updateValueAndValidity();
    });

    // Watch for domain changes to re-validate slug
    this.deeplinkForm.get('domainId')?.valueChanges.subscribe(() => {
      const slugControl = this.deeplinkForm.get('slug');
      if (slugControl?.value) {
        slugControl.updateValueAndValidity();
      }
    });
  }

  onSubmit() {
    if (this.deeplinkForm.valid) {
      const formValue = this.deeplinkForm.value;

      // Convert dates to proper format if they exist
      if (formValue.options?.activation?.activationInstant) {
        // datetime-local input returns string in format "YYYY-MM-DDTHH:mm"
        // We need to convert it to ISO string for the API
        const activationDateTime = new Date(formValue.options.activation.activationInstant);
        if (!isNaN(activationDateTime.getTime())) {
          formValue.options.activation.activationInstant = activationDateTime.toISOString();
        } else {
          formValue.options.activation.activationInstant = null;
        }
      }

      if (formValue.options?.expiry?.expiryDate) {
        const expiryDate = new Date(formValue.options.expiry.expiryDate);
        if (!isNaN(expiryDate.getTime())) {
          // Use toISOString and extract date part for consistent YYYY-MM-DD format
          formValue.options.expiry.expiryDate = expiryDate.toISOString().split('T')[0];
        } else {
          formValue.options.expiry.expiryDate = null;
        }
      }

      // Clean up empty options
      this.cleanupEmptyOptions(formValue.options);

      this.dialogRef.close(formValue);
    }
  }

  private cleanupEmptyOptions(options: OptionsDto) {
    // Remove empty activation options
    if (options.activation &&
        (!options.activation.activationInstant || options.activation.activationInstant === null) &&
        (!options.activation.targetUrlBeforeActivation || options.activation.targetUrlBeforeActivation.trim() === '')) {
      delete options.activation;
    }

    // Remove empty landing options
    if (options.landing && (!options.landing.templateId || options.landing.templateId.trim() === '')) {
      delete options.landing;
    }

    // Remove empty expiry options
    if (options.expiry && (!options.expiry.expiryDate || options.expiry.expiryDate === null)) {
      delete options.expiry;
    }

    // Remove empty password
    if (!options.password || !options.password.password || options.password.password.trim() === '') {
      delete options.password;
    }
  }

  onCancel() {
    this.dialogRef.close();
  }

  get isWithLanding() {
    return this.deeplinkForm.get('type')?.value === DeeplinkType.WITH_LANDING;
  }

  get hasActivationOptions() {
    const activation = this.deeplinkForm.get('options.activation');
    return activation?.get('activationInstant')?.value || activation?.get('targetUrlBeforeActivation')?.value;
  }

  private convertISOToDatetimeLocal(isoString: string): string {
    // Convert ISO string to datetime-local format (YYYY-MM-DDTHH:mm)
    const date = new Date(isoString);
    if (isNaN(date.getTime())) {
      return '';
    }

    // Get local datetime in the format required by datetime-local input
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }
}
