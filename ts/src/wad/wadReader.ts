import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import type { DirectoryEntry } from "./wadParser.js";
import type { LineDef, Node, Sector, Seg, SideDef, SubSector, Thing, Vertex } from "./wadModels.js";

export interface LevelModel {
  name: string;
  vertexes: Vertex[];
  lineDefs: LineDef[];
  sideDefs: SideDef[];
  sectors: Sector[];
  things: Thing[];
  nodes: Node[];
  subSectors: SubSector[];
  segs: Seg[];
}

export class WadReader {
  private readonly filePath: string;
  private header: { identification: string; numLumps: number; infoTableOffset: number } | null = null;
  private directory: DirectoryEntry[] = [];
  private wad: Buffer | null = null;

  constructor(filePath: string) {
    this.filePath = filePath;
  }

  exportLevelsAsJson(outputDir: string = "WADTool/data"): boolean {
    try {
      this.wad = readFileSync(this.filePath);
      this.readHeader();
      this.readDirectory();
      const levels = this.detectLevels();
      if (levels.length === 0) return false;

      const out = resolve(outputDir);
      mkdirSync(out, { recursive: true });
      const baseName = basename(this.filePath).replace(/\.[^.]+$/, "");
      levels.forEach((levelName, index) => {
        const model = this.readLevel(levelName);
        const outPath = join(out, `${baseName}_${index + 1}.json`);
        writeFileSync(outPath, `${JSON.stringify(model, null, 2)}\n`, "utf-8");
      });
      return true;
    } catch {
      return false;
    }
  }

  private readHeader(): void {
    const wad = must(this.wad);
    this.header = {
      identification: ascii(wad, 0, 4),
      numLumps: wad.readInt32LE(4),
      infoTableOffset: wad.readInt32LE(8),
    };
  }

  private readDirectory(): void {
    const wad = must(this.wad);
    const header = must(this.header);
    this.directory = [];
    for (let i = 0; i < header.numLumps; i += 1) {
      const base = header.infoTableOffset + i * 16;
      this.directory.push({
        filePos: wad.readInt32LE(base),
        size: wad.readInt32LE(base + 4),
        name: ascii(wad, base + 8, 8).trim(),
      });
    }
  }

  private detectLevels(): string[] {
    const mapRegex = /^MAP[0-9]{2}$/;
    const episodeRegex = /^E[0-9]M[0-9]$/;
    return this.directory
      .filter((entry) => mapRegex.test(entry.name) || episodeRegex.test(entry.name))
      .map((entry) => entry.name);
  }

  private readLevel(levelMarker: string): LevelModel {
    const markerIndex = this.directory.findIndex((entry) => entry.name === levelMarker);
    if (markerIndex < 0) {
      return emptyLevel(levelMarker);
    }

    const mapRegex = /^MAP[0-9]{2}$/;
    const episodeRegex = /^E[0-9]M[0-9]$/;
    let i = markerIndex + 1;
    let vertexes: Vertex[] = [];
    let lineDefs: LineDef[] = [];
    let sideDefs: SideDef[] = [];
    let sectors: Sector[] = [];
    let things: Thing[] = [];
    let nodes: Node[] = [];
    let subSectors: SubSector[] = [];
    let segs: Seg[] = [];

    while (i < this.directory.length) {
      const entry = this.directory[i];
      if (mapRegex.test(entry.name) || episodeRegex.test(entry.name)) break;
      switch (entry.name) {
        case "VERTEXES":
          vertexes = parseVertexes(must(this.wad), entry);
          break;
        case "LINEDEFS":
          lineDefs = parseLineDefs(must(this.wad), entry);
          break;
        case "SIDEDEFS":
          sideDefs = parseSideDefs(must(this.wad), entry);
          break;
        case "SECTORS":
          sectors = parseSectors(must(this.wad), entry);
          break;
        case "THINGS":
          things = parseThings(must(this.wad), entry);
          break;
        case "NODES":
          nodes = parseNodes(must(this.wad), entry);
          break;
        case "SSECTORS":
          subSectors = parseSubSectors(must(this.wad), entry);
          break;
        case "SEGS":
          segs = parseSegs(must(this.wad), entry);
          break;
        default:
          break;
      }
      i += 1;
    }

    return { name: levelMarker, vertexes, lineDefs, sideDefs, sectors, things, nodes, subSectors, segs };
  }
}

function emptyLevel(name: string): LevelModel {
  return { name, vertexes: [], lineDefs: [], sideDefs: [], sectors: [], things: [], nodes: [], subSectors: [], segs: [] };
}

function parseVertexes(wad: Buffer, entry: DirectoryEntry): Vertex[] {
  const count = Math.floor(entry.size / 4);
  const items: Vertex[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 4;
    items.push({ x: wad.readInt16LE(base), y: wad.readInt16LE(base + 2) });
  }
  return items;
}

function parseLineDefs(wad: Buffer, entry: DirectoryEntry): LineDef[] {
  const count = Math.floor(entry.size / 14);
  const items: LineDef[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 14;
    items.push({
      startVertex: wad.readUInt16LE(base),
      endVertex: wad.readUInt16LE(base + 2),
      flags: wad.readInt16LE(base + 4),
      specialType: wad.readInt16LE(base + 6),
      sectorTag: wad.readInt16LE(base + 8),
      rightSideDef: wad.readUInt16LE(base + 10),
      leftSideDef: wad.readUInt16LE(base + 12),
    });
  }
  return items;
}

function parseSideDefs(wad: Buffer, entry: DirectoryEntry): SideDef[] {
  const count = Math.floor(entry.size / 30);
  const items: SideDef[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 30;
    items.push({
      xOffset: wad.readInt16LE(base),
      yOffset: wad.readInt16LE(base + 2),
      upperTexture: ascii(wad, base + 4, 8).trim(),
      lowerTexture: ascii(wad, base + 12, 8).trim(),
      middleTexture: ascii(wad, base + 20, 8).trim(),
      sector: wad.readUInt16LE(base + 28),
    });
  }
  return items;
}

function parseSectors(wad: Buffer, entry: DirectoryEntry): Sector[] {
  const count = Math.floor(entry.size / 26);
  const items: Sector[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 26;
    items.push({
      floorHeight: wad.readInt16LE(base),
      ceilingHeight: wad.readInt16LE(base + 2),
      floorTexture: ascii(wad, base + 4, 8).trim(),
      ceilingTexture: ascii(wad, base + 12, 8).trim(),
      lightLevel: wad.readInt16LE(base + 20),
      specialType: wad.readInt16LE(base + 22),
      tag: wad.readInt16LE(base + 24),
    });
  }
  return items;
}

function parseThings(wad: Buffer, entry: DirectoryEntry): Thing[] {
  const count = Math.floor(entry.size / 10);
  const items: Thing[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 10;
    items.push({
      x: wad.readInt16LE(base),
      y: wad.readInt16LE(base + 2),
      angle: wad.readInt16LE(base + 4),
      type: wad.readInt16LE(base + 6),
      flags: wad.readInt16LE(base + 8),
    });
  }
  return items;
}

function parseNodes(wad: Buffer, entry: DirectoryEntry): Node[] {
  const count = Math.floor(entry.size / 28);
  const items: Node[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 28;
    items.push({
      xPartition: wad.readInt16LE(base),
      yPartition: wad.readInt16LE(base + 2),
      xChange: wad.readInt16LE(base + 4),
      yChange: wad.readInt16LE(base + 6),
      rightBoxTop: wad.readInt16LE(base + 8),
      rightBoxBottom: wad.readInt16LE(base + 10),
      rightBoxLeft: wad.readInt16LE(base + 12),
      rightBoxRight: wad.readInt16LE(base + 14),
      leftBoxTop: wad.readInt16LE(base + 16),
      leftBoxBottom: wad.readInt16LE(base + 18),
      leftBoxLeft: wad.readInt16LE(base + 20),
      leftBoxRight: wad.readInt16LE(base + 22),
      rightChild: wad.readUInt16LE(base + 24),
      leftChild: wad.readUInt16LE(base + 26),
    });
  }
  return items;
}

function parseSubSectors(wad: Buffer, entry: DirectoryEntry): SubSector[] {
  const count = Math.floor(entry.size / 4);
  const items: SubSector[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 4;
    items.push({ segCount: wad.readInt16LE(base), firstSeg: wad.readUInt16LE(base + 2) });
  }
  return items;
}

function parseSegs(wad: Buffer, entry: DirectoryEntry): Seg[] {
  const count = Math.floor(entry.size / 12);
  const items: Seg[] = [];
  for (let i = 0; i < count; i += 1) {
    const base = entry.filePos + i * 12;
    items.push({
      startVertex: wad.readUInt16LE(base),
      endVertex: wad.readUInt16LE(base + 2),
      angle: wad.readInt16LE(base + 4),
      lineDef: wad.readUInt16LE(base + 6),
      direction: wad.readInt16LE(base + 8),
      offset: wad.readInt16LE(base + 10),
    });
  }
  return items;
}

function ascii(buffer: Buffer, offset: number, length: number): string {
  return buffer.subarray(offset, offset + length).toString("ascii").replace(/\0/g, "").trim();
}

function must<T>(value: T | null): T {
  if (value == null) throw new Error("Expected value");
  return value;
}
