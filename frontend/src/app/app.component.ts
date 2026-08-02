import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { FurnitureBoxEditorComponent } from './furniture-box-editor.component';
import { SpatialApiService } from './spatial-api.service';
import {
  FurnitureEditorState,
  FurnitureInstanceDto,
  IDENTITY_TRANSFORM
} from './spatial.models';

@Component({
  selector: 'arh-root',
  standalone: true,
  imports: [ReactiveFormsModule, FurnitureBoxEditorComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppComponent {
  readonly categories = [
    { value: 'WARDROBE', label: 'Armario' },
    { value: 'CABINET', label: 'Mueble / gabinete' },
    { value: 'SHELVING', label: 'Estantería' },
    { value: 'DRAWER_UNIT', label: 'Cajonera' },
    { value: 'DESK', label: 'Escritorio' },
    { value: 'OTHER', label: 'Otro' }
  ];

  readonly spaceForm;
  readonly furnitureForm;
  readonly creatingSpace = signal(false);
  readonly savingFurniture = signal(false);
  readonly createdFurniture = signal<FurnitureInstanceDto | null>(null);
  readonly errorMessage = signal<string | null>(null);

  editorState: FurnitureEditorState = {
    spaceTransform: {
      translation: { x: 0, y: 1.1, z: 0 },
      rotation: { x: 0, y: 0, z: 0, w: 1 }
    },
    bounds: { width: 2, height: 2.2, depth: 0.6 },
    yawDegrees: 0
  };

  constructor(
    formBuilder: FormBuilder,
    private readonly spatialApi: SpatialApiService
  ) {
    this.spaceForm = formBuilder.nonNullable.group({
      spaceName: ['Vivienda principal', [Validators.required, Validators.maxLength(120)]],
      spaceId: ['', [Validators.required]]
    });
    this.furnitureForm = formBuilder.nonNullable.group({
      furnitureName: ['Armario dormitorio', [Validators.required, Validators.maxLength(120)]],
      category: ['WARDROBE', [Validators.required]]
    });
  }

  onEditorChange(state: FurnitureEditorState): void {
    this.editorState = state;
  }

  createSpace(): void {
    const spaceName = this.spaceForm.controls.spaceName.value.trim();
    if (!spaceName || this.creatingSpace()) {
      return;
    }

    this.errorMessage.set(null);
    this.creatingSpace.set(true);
    this.spatialApi
      .createSpace({ name: spaceName, worldTransform: IDENTITY_TRANSFORM })
      .pipe(finalize(() => this.creatingSpace.set(false)))
      .subscribe({
        next: (space) => {
          this.spaceForm.controls.spaceId.setValue(space.id);
          this.spaceForm.controls.spaceId.markAsDirty();
        },
        error: (error: unknown) => this.errorMessage.set(this.readError(error))
      });
  }

  saveFurniture(): void {
    this.spaceForm.controls.spaceId.markAsTouched();
    this.furnitureForm.markAllAsTouched();
    if (
      this.spaceForm.controls.spaceId.invalid ||
      this.furnitureForm.invalid ||
      this.savingFurniture()
    ) {
      return;
    }

    const spaceId = this.spaceForm.controls.spaceId.value.trim();
    const furnitureName = this.furnitureForm.controls.furnitureName.value.trim();
    this.errorMessage.set(null);
    this.createdFurniture.set(null);
    this.savingFurniture.set(true);

    this.spatialApi
      .registerFurniture(spaceId, {
        name: furnitureName,
        category: this.furnitureForm.controls.category.value,
        spaceTransform: this.editorState.spaceTransform,
        bounds: this.editorState.bounds,
        recognitionMode: 'MANUAL_BOUNDING_BOX',
        confidence: 1,
        visualDescriptor: null
      })
      .pipe(finalize(() => this.savingFurniture.set(false)))
      .subscribe({
        next: (furniture) => this.createdFurniture.set(furniture),
        error: (error: unknown) => this.errorMessage.set(this.readError(error))
      });
  }

  format(value: number): string {
    return new Intl.NumberFormat('es-ES', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 3
    }).format(value);
  }

  private readError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const detail = error.error as { detail?: string; title?: string } | string | null;
      if (typeof detail === 'string' && detail.trim()) {
        return detail;
      }
      if (detail && typeof detail === 'object') {
        return detail.detail ?? detail.title ?? `Error HTTP ${error.status}`;
      }
      return error.message || `Error HTTP ${error.status}`;
    }
    return error instanceof Error ? error.message : 'No se pudo completar la operación.';
  }
}
