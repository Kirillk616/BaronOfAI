export interface Vertex {
  x: number;
  y: number;
}

export interface LineDef {
  startVertex: number;
  endVertex: number;
  flags: number;
  specialType: number;
  sectorTag: number;
  rightSideDef: number;
  leftSideDef: number;
}

export interface SideDef {
  xOffset: number;
  yOffset: number;
  upperTexture: string;
  lowerTexture: string;
  middleTexture: string;
  sector: number;
}

export interface Sector {
  floorHeight: number;
  ceilingHeight: number;
  floorTexture: string;
  ceilingTexture: string;
  lightLevel: number;
  specialType: number;
  tag: number;
}

export interface Thing {
  x: number;
  y: number;
  angle: number;
  type: number;
  flags: number;
}

export interface Node {
  xPartition: number;
  yPartition: number;
  xChange: number;
  yChange: number;
  rightBoxTop: number;
  rightBoxBottom: number;
  rightBoxLeft: number;
  rightBoxRight: number;
  leftBoxTop: number;
  leftBoxBottom: number;
  leftBoxLeft: number;
  leftBoxRight: number;
  rightChild: number;
  leftChild: number;
}

export interface SubSector {
  segCount: number;
  firstSeg: number;
}

export interface Seg {
  startVertex: number;
  endVertex: number;
  angle: number;
  lineDef: number;
  direction: number;
  offset: number;
}

export interface MapAnnotation {
  text: string;
  x: number;
  y: number;
  title: string;
}
