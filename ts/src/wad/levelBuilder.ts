import type { LineDef, MapAnnotation, Node, Sector, Seg, SideDef, SubSector, Thing, Vertex } from "./wadModels.js";

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
