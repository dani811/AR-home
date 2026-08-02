import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  NgZone,
  OnDestroy,
  Output,
  ViewChild,
  inject
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { TransformControls } from 'three/addons/controls/TransformControls.js';

import {
  CameraAccessError,
  CameraDevice,
  CameraSession,
  CameraStreamService
} from './camera-stream.service';
import { FurnitureEditorState } from './spatial.models';

type TransformMode = 'translate' | 'rotate' | 'scale';
type ViewMode = 'scene' | 'camera';

type DisposableRenderable = THREE.Object3D & {
  geometry?: THREE.BufferGeometry;
  material?: THREE.Material | THREE.Material[];
};

@Component({
  selector: 'arh-furniture-box-editor',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './furniture-box-editor.component.html',
  styleUrl: './furniture-box-editor.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FurnitureBoxEditorComponent implements AfterViewInit, OnDestroy {
  @ViewChild('viewport', { static: true })
  private readonly viewportRef!: ElementRef<HTMLDivElement>;

  @ViewChild('cameraVideo', { static: true })
  private readonly cameraVideoRef!: ElementRef<HTMLVideoElement>;

  @Output() readonly editorChange = new EventEmitter<FurnitureEditorState>();

  mode: TransformMode = 'translate';
  viewMode: ViewMode = 'scene';
  positionX = 0;
  positionY = 1.1;
  positionZ = 0;
  yawDegrees = 0;
  width = 2;
  height = 2.2;
  depth = 0.6;

  readonly cameraSupported: boolean;
  readonly secureContext: boolean;
  cameraDevices: readonly CameraDevice[] = [];
  selectedCameraId = '';
  cameraError: string | null = null;
  cameraDetails: string | null = null;
  cameraActive = false;
  cameraStarting = false;
  mirrorVideo = false;

  private readonly cameraStream = inject(CameraStreamService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly ngZone = inject(NgZone);
  private readonly handleDeviceChange = (): void => {
    if (this.cameraActive) {
      void this.refreshCameraDevices();
    }
  };

  private scene?: THREE.Scene;
  private camera?: THREE.PerspectiveCamera;
  private renderer?: THREE.WebGLRenderer;
  private orbitControls?: OrbitControls;
  private transformControls?: TransformControls;
  private furnitureMesh?: THREE.Mesh<THREE.BoxGeometry, THREE.MeshStandardMaterial>;
  private environmentGroup?: THREE.Group;
  private resizeObserver?: ResizeObserver;
  private cameraPoseInitialized = false;

  constructor() {
    this.cameraSupported = this.cameraStream.isSupported();
    this.secureContext = this.cameraStream.isSecureContext();
  }

  ngAfterViewInit(): void {
    this.ngZone.runOutsideAngular(() => this.initializeScene());
    navigator.mediaDevices?.addEventListener?.('devicechange', this.handleDeviceChange);
    this.emitState();
  }

  ngOnDestroy(): void {
    navigator.mediaDevices?.removeEventListener?.('devicechange', this.handleDeviceChange);
    this.cameraStream.stop();
    this.detachVideoStream();
    this.resizeObserver?.disconnect();
    this.renderer?.setAnimationLoop(null);
    this.orbitControls?.dispose();
    this.transformControls?.dispose();

    this.scene?.traverse((object) => {
      const renderable = object as DisposableRenderable;
      renderable.geometry?.dispose();
      const materials = renderable.material
        ? Array.isArray(renderable.material)
          ? renderable.material
          : [renderable.material]
        : [];
      materials.forEach((material) => material.dispose());
    });

    this.renderer?.dispose();
    this.renderer?.domElement.remove();
  }

  setMode(mode: TransformMode): void {
    this.mode = mode;
    this.transformControls?.setMode(mode);
  }

  async startCamera(deviceId?: string): Promise<void> {
    if (this.cameraStarting || !this.cameraSupported) {
      return;
    }

    if (!this.secureContext) {
      this.cameraError =
        'La cámara requiere HTTPS o localhost. Abre la aplicación desde un origen seguro.';
      this.changeDetector.markForCheck();
      return;
    }

    this.cameraStarting = true;
    this.cameraError = null;
    this.cameraDetails = null;
    this.changeDetector.markForCheck();

    try {
      const session = await this.cameraStream.start(deviceId || this.selectedCameraId || undefined);
      await this.attachVideoStream(session);
      this.cameraActive = true;
      this.selectedCameraId = session.deviceId ?? this.selectedCameraId;
      this.mirrorVideo = session.facingMode === 'user';
      this.cameraDetails = this.describeSession(session);
      await this.refreshCameraDevices();
      this.enterCameraView();
    } catch (error: unknown) {
      this.cameraStream.stop();
      this.detachVideoStream();
      this.cameraActive = false;
      this.cameraError =
        error instanceof CameraAccessError
          ? error.message
          : error instanceof Error
            ? error.message
            : 'No se pudo iniciar la cámara.';
      this.enterSceneView();
    } finally {
      this.cameraStarting = false;
      this.changeDetector.markForCheck();
    }
  }

  async switchCamera(deviceId: string): Promise<void> {
    this.selectedCameraId = deviceId;
    if (this.cameraActive) {
      await this.startCamera(deviceId);
    }
  }

  stopCamera(): void {
    this.cameraStream.stop();
    this.detachVideoStream();
    this.cameraActive = false;
    this.cameraStarting = false;
    this.cameraDetails = null;
    this.mirrorVideo = false;
    this.enterSceneView();
    this.changeDetector.markForCheck();
  }

  applyNumericState(): void {
    if (!this.furnitureMesh) {
      return;
    }

    this.width = this.clampDimension(this.width);
    this.height = this.clampDimension(this.height);
    this.depth = this.clampDimension(this.depth);
    this.positionX = this.finiteOrZero(this.positionX);
    this.positionY = this.finiteOrZero(this.positionY);
    this.positionZ = this.finiteOrZero(this.positionZ);
    this.yawDegrees = this.finiteOrZero(this.yawDegrees);

    this.furnitureMesh.position.set(this.positionX, this.positionY, this.positionZ);
    this.furnitureMesh.quaternion.setFromEuler(
      new THREE.Euler(0, THREE.MathUtils.degToRad(this.yawDegrees), 0, 'YXZ')
    );
    this.furnitureMesh.scale.set(this.width, this.height, this.depth);
    this.emitState();
  }

  reset(): void {
    this.width = 2;
    this.height = 2.2;
    this.depth = 0.6;
    this.positionX = 0;
    this.positionY = this.viewMode === 'camera' ? 0 : this.height / 2;
    this.positionZ = this.viewMode === 'camera' ? -3 : 0;
    this.yawDegrees = 0;
    this.applyNumericState();

    if (this.camera && this.orbitControls && this.viewMode === 'scene') {
      this.camera.position.set(4, 3, 5);
      this.orbitControls.target.set(0, 1, 0);
      this.orbitControls.update();
    }
  }

  private initializeScene(): void {
    const viewport = this.viewportRef.nativeElement;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x07111e);
    this.scene.fog = new THREE.Fog(0x07111e, 10, 24);

    this.camera = new THREE.PerspectiveCamera(50, 1, 0.05, 100);
    this.camera.position.set(4, 3, 5);

    this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    this.renderer.setClearColor(0x07111e, 1);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    viewport.appendChild(this.renderer.domElement);

    const hemisphereLight = new THREE.HemisphereLight(0xb9e8ff, 0x172032, 2.2);
    this.scene.add(hemisphereLight);

    const keyLight = new THREE.DirectionalLight(0xffffff, 3.2);
    keyLight.position.set(3, 6, 4);
    keyLight.castShadow = true;
    this.scene.add(keyLight);

    this.environmentGroup = new THREE.Group();
    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(12, 12),
      new THREE.MeshStandardMaterial({
        color: 0x0d1a2a,
        roughness: 0.92,
        metalness: 0.05
      })
    );
    floor.rotation.x = -Math.PI / 2;
    floor.receiveShadow = true;
    this.environmentGroup.add(floor);

    const grid = new THREE.GridHelper(12, 24, 0x34d6ff, 0x244158);
    grid.position.y = 0.002;
    this.environmentGroup.add(grid);
    this.scene.add(this.environmentGroup);

    const geometry = new THREE.BoxGeometry(1, 1, 1);
    const material = new THREE.MeshStandardMaterial({
      color: 0x00c8ff,
      transparent: true,
      opacity: 0.28,
      roughness: 0.35,
      metalness: 0.1,
      depthTest: true
    });

    this.furnitureMesh = new THREE.Mesh(geometry, material);
    this.furnitureMesh.castShadow = true;
    this.furnitureMesh.position.set(this.positionX, this.positionY, this.positionZ);
    this.furnitureMesh.scale.set(this.width, this.height, this.depth);

    const edgeSourceGeometry = new THREE.BoxGeometry(1, 1, 1);
    const edgesGeometry = new THREE.EdgesGeometry(edgeSourceGeometry);
    edgeSourceGeometry.dispose();
    const edges = new THREE.LineSegments(
      edgesGeometry,
      new THREE.LineBasicMaterial({ color: 0x7eeeff })
    );
    this.furnitureMesh.add(edges);
    this.scene.add(this.furnitureMesh);

    this.orbitControls = new OrbitControls(this.camera, this.renderer.domElement);
    this.orbitControls.enableDamping = true;
    this.orbitControls.dampingFactor = 0.08;
    this.orbitControls.target.set(0, 1, 0);
    this.orbitControls.maxPolarAngle = Math.PI / 2 - 0.02;
    this.orbitControls.minDistance = 1.2;
    this.orbitControls.maxDistance = 15;

    this.transformControls = new TransformControls(this.camera, this.renderer.domElement);
    this.transformControls.setMode(this.mode);
    this.transformControls.setSpace('local');
    this.transformControls.setTranslationSnap(0.05);
    this.transformControls.setRotationSnap(THREE.MathUtils.degToRad(5));
    this.transformControls.setScaleSnap(0.05);
    this.transformControls.attach(this.furnitureMesh);
    this.scene.add(this.transformControls.getHelper());

    this.transformControls.addEventListener('dragging-changed', (event) => {
      if (this.orbitControls) {
        this.orbitControls.enabled =
          this.viewMode === 'scene' && !(event as unknown as { value: boolean }).value;
      }
    });
    this.transformControls.addEventListener('objectChange', () => {
      this.ngZone.run(() => this.syncFromMesh());
    });

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(viewport);
    this.resize();

    this.renderer.setAnimationLoop(() => {
      if (this.viewMode === 'scene') {
        this.orbitControls?.update();
      }
      if (this.scene && this.camera) {
        this.renderer?.render(this.scene, this.camera);
      }
    });
  }

  private enterCameraView(): void {
    if (!this.scene || !this.camera || !this.renderer || !this.furnitureMesh) {
      return;
    }

    this.viewMode = 'camera';
    this.scene.background = null;
    this.scene.fog = null;
    this.environmentGroup?.visible && (this.environmentGroup.visible = false);
    this.renderer.setClearColor(0x000000, 0);
    this.camera.fov = 60;
    this.camera.position.set(0, 0, 0);
    this.camera.quaternion.identity();
    this.camera.updateProjectionMatrix();
    this.camera.updateMatrixWorld(true);

    if (this.orbitControls) {
      this.orbitControls.enabled = false;
      this.orbitControls.target.set(0, 0, -3);
    }

    if (!this.cameraPoseInitialized) {
      this.furnitureMesh.position.set(0, 0, -3);
      this.furnitureMesh.quaternion.identity();
      this.cameraPoseInitialized = true;
      this.syncFromMesh();
    } else {
      this.emitState();
    }
  }

  private enterSceneView(): void {
    if (!this.scene || !this.camera || !this.renderer) {
      return;
    }

    this.viewMode = 'scene';
    this.scene.background = new THREE.Color(0x07111e);
    this.scene.fog = new THREE.Fog(0x07111e, 10, 24);
    if (this.environmentGroup) {
      this.environmentGroup.visible = true;
    }
    this.renderer.setClearColor(0x07111e, 1);
    this.camera.fov = 50;
    this.camera.position.set(4, 3, 5);
    this.camera.updateProjectionMatrix();

    if (this.orbitControls) {
      this.orbitControls.enabled = true;
      if (this.furnitureMesh) {
        this.orbitControls.target.copy(this.furnitureMesh.position);
      }
      this.orbitControls.update();
    }
    this.emitState();
  }

  private async attachVideoStream(session: CameraSession): Promise<void> {
    const video = this.cameraVideoRef.nativeElement;
    video.muted = true;
    video.playsInline = true;
    video.srcObject = session.stream;
    await video.play();
  }

  private detachVideoStream(): void {
    const video = this.cameraVideoRef.nativeElement;
    video.pause();
    video.srcObject = null;
  }

  private async refreshCameraDevices(): Promise<void> {
    try {
      this.cameraDevices = await this.cameraStream.listVideoInputs();
      if (!this.selectedCameraId && this.cameraDevices.length > 0) {
        this.selectedCameraId = this.cameraDevices[0].deviceId;
      }
    } catch {
      this.cameraDevices = [];
    }
    this.changeDetector.markForCheck();
  }

  private describeSession(session: CameraSession): string {
    const resolution =
      session.width && session.height ? `${session.width} × ${session.height}` : 'resolución automática';
    const facing = session.facingMode === 'environment' ? 'trasera' : session.facingMode === 'user' ? 'frontal' : null;
    return facing ? `${resolution} · cámara ${facing}` : resolution;
  }

  private resize(): void {
    if (!this.renderer || !this.camera) {
      return;
    }

    const viewport = this.viewportRef.nativeElement;
    const width = Math.max(viewport.clientWidth, 1);
    const height = Math.max(viewport.clientHeight, 1);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  private syncFromMesh(): void {
    if (!this.furnitureMesh) {
      return;
    }

    this.positionX = this.round(this.furnitureMesh.position.x);
    this.positionY = this.round(this.furnitureMesh.position.y);
    this.positionZ = this.round(this.furnitureMesh.position.z);
    this.width = this.clampDimension(Math.abs(this.furnitureMesh.scale.x));
    this.height = this.clampDimension(Math.abs(this.furnitureMesh.scale.y));
    this.depth = this.clampDimension(Math.abs(this.furnitureMesh.scale.z));

    const euler = new THREE.Euler().setFromQuaternion(this.furnitureMesh.quaternion, 'YXZ');
    this.yawDegrees = this.round(THREE.MathUtils.radToDeg(euler.y), 1);
    this.changeDetector.markForCheck();
    this.emitState();
  }

  private emitState(): void {
    if (!this.furnitureMesh) {
      return;
    }

    const quaternion = this.furnitureMesh.quaternion.clone().normalize();
    this.editorChange.emit({
      spaceTransform: {
        translation: {
          x: this.round(this.furnitureMesh.position.x),
          y: this.round(this.furnitureMesh.position.y),
          z: this.round(this.furnitureMesh.position.z)
        },
        rotation: {
          x: this.round(quaternion.x, 6),
          y: this.round(quaternion.y, 6),
          z: this.round(quaternion.z, 6),
          w: this.round(quaternion.w, 6)
        }
      },
      bounds: {
        width: this.clampDimension(Math.abs(this.furnitureMesh.scale.x)),
        height: this.clampDimension(Math.abs(this.furnitureMesh.scale.y)),
        depth: this.clampDimension(Math.abs(this.furnitureMesh.scale.z))
      },
      yawDegrees: this.yawDegrees,
      referenceFrame: this.viewMode === 'camera' ? 'CAMERA_PREVIEW' : 'SPACE'
    });
  }

  private clampDimension(value: number): number {
    return this.round(Math.min(Math.max(Number.isFinite(value) ? Math.abs(value) : 0.1, 0.1), 20));
  }

  private finiteOrZero(value: number): number {
    return Number.isFinite(value) ? value : 0;
  }

  private round(value: number, digits = 3): number {
    const factor = 10 ** digits;
    return Math.round(value * factor) / factor;
  }
}
