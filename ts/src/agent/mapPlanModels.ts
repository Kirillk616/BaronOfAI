export interface MapPromptProfile {
  originalPrompt: string;
  title: string;
  theme: string;
  difficulty: string;
  targetRoomCount: number;
  requestedKeys: string[];
  requestedSetPieces: string[];
  lightingHint: string;
}

export interface MapPlan {
  profile: MapPromptProfile;
  topology: RoomNode[];
  connections: ConnectionPlan[];
  rooms: RoomPlan[];
  validation: ValidationReport;
}

export interface RoomNode {
  id: string;
  label: string;
  role: string;
  required: boolean;
  lock?: string;
  grantsKey?: string;
  notes: string;
}

export interface ConnectionPlan {
  from: string;
  to: string;
  kind: string;
  requiredKey?: string;
  notes: string;
}

export interface RoomPlan {
  id: string;
  shape: string;
  width: number;
  height: number;
  floorHeight: number;
  ceilingHeight: number;
  light: number;
  floorTexture: string;
  ceilingTexture: string;
  wallTexture: string;
  encounterHint: string;
  traversalHint: string;
  landmark: string;
}

export interface ValidationReport {
  passed: boolean;
  score: number;
  hardIssues: string[];
  softIssues: string[];
}

export function defaultValidationReport(): ValidationReport {
  return {
    passed: false,
    score: 0,
    hardIssues: [],
    softIssues: [],
  };
}
