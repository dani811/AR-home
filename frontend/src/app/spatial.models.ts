export interface Vector3Dto {
  x: number;
  y: number;
  z: number;
}

export interface QuaternionDto {
  x: number;
  y: number;
  z: number;
  w: number;
}

export interface Transform3DDto {
  translation: Vector3Dto;
  rotation: QuaternionDto;
}

export interface Bounds3DDto {
  width: number;
  height: number;
  depth: number;
}

export interface SpaceDto {
  id: string;
  name: string;
  worldTransform: Transform3DDto;
  createdAt: string;
  updatedAt: string;
}

export interface FurnitureInstanceDto {
  id: string;
  spaceId: string;
  name: string;
  category: string;
  spaceTransform: Transform3DDto;
  bounds: Bounds3DDto;
  recognitionMode: RecognitionMode;
  confidence: number;
  visualDescriptor: string | null;
  createdAt: string;
  updatedAt: string;
}

export type RecognitionMode =
  | 'MANUAL_BOUNDING_BOX'
  | 'VISUAL_RELOCALIZATION'
  | 'MARKER_ASSISTED'
  | 'AUTOMATIC';

export interface CreateSpaceRequest {
  name: string;
  worldTransform: Transform3DDto;
}

export interface RegisterFurnitureRequest {
  name: string;
  category: string;
  spaceTransform: Transform3DDto;
  bounds: Bounds3DDto;
  recognitionMode: RecognitionMode;
  confidence: number;
  visualDescriptor: string | null;
}

export interface FurnitureEditorState {
  spaceTransform: Transform3DDto;
  bounds: Bounds3DDto;
  yawDegrees: number;
}

export const IDENTITY_TRANSFORM: Transform3DDto = {
  translation: { x: 0, y: 0, z: 0 },
  rotation: { x: 0, y: 0, z: 0, w: 1 }
};
