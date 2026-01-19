import { Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule, FormBuilder, FormArray, FormGroup } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

interface MetadataEntry {
  key: string;
  value: string;
}

@Component({
  selector: 'app-metadata-editor',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule
  ],
  templateUrl: './metadata-editor.html',
  styleUrl: './metadata-editor.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MetadataEditorComponent),
      multi: true
    }
  ]
})
export class MetadataEditorComponent implements ControlValueAccessor {
  metadataArray: FormArray;
  private onChange: (value: Record<string, string>) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private fb: FormBuilder) {
    this.metadataArray = this.fb.array([]);
  }

  get entries(): FormGroup[] {
    return this.metadataArray.controls as FormGroup[];
  }

  writeValue(value: Record<string, string> | null): void {
    this.metadataArray.clear();
    if (value && typeof value === 'object') {
      Object.entries(value).forEach(([key, val]) => {
        this.metadataArray.push(this.createEntry(key, val));
      });
    }
  }

  registerOnChange(fn: (value: Record<string, string>) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    if (isDisabled) {
      this.metadataArray.disable();
    } else {
      this.metadataArray.enable();
    }
  }

  addEntry(): void {
    this.metadataArray.push(this.createEntry('', ''));
    this.emitChange();
  }

  removeEntry(index: number): void {
    this.metadataArray.removeAt(index);
    this.emitChange();
  }

  onEntryChange(): void {
    this.emitChange();
  }

  private createEntry(key: string = '', value: string = ''): FormGroup {
    return this.fb.group({
      key: [key],
      value: [value]
    });
  }

  private emitChange(): void {
    const metadata: Record<string, string> = {};
    this.metadataArray.controls.forEach((control) => {
      const key = control.get('key')?.value?.trim();
      const value = control.get('value')?.value?.trim();
      if (key) {
        metadata[key] = value || '';
      }
    });
    this.onChange(metadata);
    this.onTouched();
  }
}