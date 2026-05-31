import type { LineDef, MapAnnotation, Node, Sector, Seg, SideDef, SubSector, Thing, Vertex } from "./wadModels.js";

export interface LevelSpec {
  roomSize: number;
  floorHeight: number;
  ceilingHeight: number;
  floorTex: string;
  ceilTex: string;
  wallTex: string;
  light: number;
  playerAngle: number;
}

export interface BuiltLevel {
  vertexes: Vertex[];
  lineDefs: LineDef[];
  sideDefs: SideDef[];
  sectors: Sector[];
  things: Thing[];
  nodes: Node[];
  subSectors: SubSector[];
  segs: Seg[];
  annotations: MapAnnotation[];
}

export function buildLevelFromSpec(spec: LevelSpec): BuiltLevel {
  const half = Math.max(64, Math.floor(spec.roomSize / 2));
  const vertexes: Vertex[] = [
    { x: -half, y: -half },
    { x: half, y: -half },
    { x: half, y: half },
    { x: -half, y: half },
  ];
  const sectors: Sector[] = [
    {
      floorHeight: spec.floorHeight,
      ceilingHeight: spec.ceilingHeight,
      floorTexture: spec.floorTex.slice(0, 8),
      ceilingTexture: spec.ceilTex.slice(0, 8),
      lightLevel: spec.light,
      specialType: 0,
      tag: 0,
    },
  ];
  const sideDefs: SideDef[] = [
    { xOffset: 0, yOffset: 0, upperTexture: "-", lowerTexture: "-", middleTexture: spec.wallTex.slice(0, 8), sector: 0 },
    { xOffset: 0, yOffset: 0, upperTexture: "-", lowerTexture: "-", middleTexture: spec.wallTex.slice(0, 8), sector: 0 },
    { xOffset: 0, yOffset: 0, upperTexture: "-", lowerTexture: "-", middleTexture: spec.wallTex.slice(0, 8), sector: 0 },
    { xOffset: 0, yOffset: 0, upperTexture: "-", lowerTexture: "-", middleTexture: spec.wallTex.slice(0, 8), sector: 0 },
  ];
  const lineDefs: LineDef[] = [
    { startVertex: 0, endVertex: 1, flags: 0, specialType: 0, sectorTag: 0, rightSideDef: 0, leftSideDef: -1 },
    { startVertex: 1, endVertex: 2, flags: 0, specialType: 0, sectorTag: 0, rightSideDef: 1, leftSideDef: -1 },
    { startVertex: 2, endVertex: 3, flags: 0, specialType: 0, sectorTag: 0, rightSideDef: 2, leftSideDef: -1 },
    { startVertex: 3, endVertex: 0, flags: 0, specialType: 0, sectorTag: 0, rightSideDef: 3, leftSideDef: -1 },
  ];
  const things: Thing[] = [{ x: 0, y: 0, angle: spec.playerAngle, type: 1, flags: 7 }];

  return {
    vertexes,
    lineDefs,
    sideDefs,
    sectors,
    things,
    nodes: [] as Node[],
    subSectors: [] as SubSector[],
    segs: [] as Seg[],
    annotations: [],
  };
}
