import { Injectable } from '@angular/core';

export interface CameraDevice {
  deviceId: string;
  label: string;
}

export interface CameraSession {
  stream: MediaStream;
  deviceId: string | null;
  width: number | null;
  height: number | null;
  facingMode: string | null;
}

@Injectable({ providedIn: 'root' })
export class CameraStreamService {
  private activeStream: MediaStream | null = null;

  isSupported(): boolean {
    return typeof navigator !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia);
  }

  isSecureContext(): boolean {
    return typeof window !== 'undefined' && window.isSecureContext;
  }

  async start(deviceId?: string): Promise<CameraSession> {
    if (!this.isSupported()) {
      throw new CameraAccessError(
        'UNSUPPORTED',
        'Este navegador no expone la API de cámara getUserMedia.'
      );
    }

    this.stop();

    const preferredConstraints: MediaTrackConstraints = deviceId
      ? {
          deviceId: { exact: deviceId },
          width: { ideal: 1920 },
          height: { ideal: 1080 },
          frameRate: { ideal: 30, max: 60 }
        }
      : {
          facingMode: { ideal: 'environment' },
          width: { ideal: 1920 },
          height: { ideal: 1080 },
          frameRate: { ideal: 30, max: 60 }
        };

    try {
      this.activeStream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: preferredConstraints
      });
    } catch (error: unknown) {
      throw this.mapError(error);
    }

    const track = this.activeStream.getVideoTracks()[0];
    if (!track) {
      this.stop();
      throw new CameraAccessError('NO_VIDEO_TRACK', 'La cámara no devolvió ninguna pista de vídeo.');
    }

    const settings = track.getSettings();
    return {
      stream: this.activeStream,
      deviceId: settings.deviceId ?? null,
      width: settings.width ?? null,
      height: settings.height ?? null,
      facingMode: settings.facingMode ?? null
    };
  }

  async listVideoInputs(): Promise<CameraDevice[]> {
    if (!this.isSupported()) {
      return [];
    }

    const devices = await navigator.mediaDevices.enumerateDevices();
    let fallbackIndex = 1;
    return devices
      .filter((device) => device.kind === 'videoinput')
      .map((device) => ({
        deviceId: device.deviceId,
        label: device.label.trim() || `Cámara ${fallbackIndex++}`
      }));
  }

  stop(): void {
    this.activeStream?.getTracks().forEach((track) => track.stop());
    this.activeStream = null;
  }

  private mapError(error: unknown): CameraAccessError {
    if (!(error instanceof DOMException)) {
      return new CameraAccessError('UNKNOWN', 'No se pudo iniciar la cámara.', error);
    }

    switch (error.name) {
      case 'NotAllowedError':
      case 'SecurityError':
        return new CameraAccessError(
          'PERMISSION_DENIED',
          'El permiso de cámara fue rechazado o la página no se está sirviendo en un contexto seguro.',
          error
        );
      case 'NotFoundError':
        return new CameraAccessError(
          'NO_CAMERA',
          'No se encontró ninguna cámara compatible.',
          error
        );
      case 'NotReadableError':
      case 'AbortError':
        return new CameraAccessError(
          'CAMERA_BUSY',
          'La cámara está siendo utilizada por otra aplicación o no puede abrirse.',
          error
        );
      case 'OverconstrainedError':
        return new CameraAccessError(
          'CONSTRAINTS',
          'La cámara seleccionada no admite la configuración solicitada.',
          error
        );
      default:
        return new CameraAccessError('UNKNOWN', error.message || 'No se pudo iniciar la cámara.', error);
    }
  }
}

export class CameraAccessError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly originalError?: unknown
  ) {
    super(message);
    this.name = 'CameraAccessError';
  }
}
