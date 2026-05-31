import { readFileSync } from "node:fs";
import type { LineDef, Node, Sector, Seg, SideDef, SubSector, Thing, Vertex } from "./wadModels.js";

export interface WadHeader {
  identification: string;
  numLumps: number;
  infoTableOffset: number;
}

export interface DirectoryEntry {
  filePos: number;
  size: number;
  name: string;
}

export class WadParser {
  readonly filePath: string;
  wadHeader: WadHeader | null = null;
  directoryEntries: DirectoryEntry[] = [];
  vertexes: Vertex[] = [];
  lineDefs: LineDef[] = [];
  sideDefs: SideDef[] = [];
  sectors: Sector[] = [];
  things: Thing[] = [];
  nodes: Node[] = [];
  subSectors: SubSector[] = [];
  segs: Seg[] = [];

  constructor(filePath: string) {
    this.filePath = filePath;
  }

  parse(): boolean {
    try {
      const buffer = readFileSync(this.filePath);
      this.readHeader(buffer);
      this.readDirectory(buffer);
      this.parseVertexes(buffer);
      this.parseLineDefs(buffer);
      this.parseSideDefs(buffer);
      this.parseSectors(buffer);
      this.parseThings(buffer);
      this.parseNodes(buffer);
      this.parseSubSectors(buffer);
      this.parseSegs(buffer);
      return true;
    } catch {
      return false;
    }
  }

  getSummary(): string {
    const header = this.wadHeader;
    if (!header) return `WAD File: ${this.filePath}\nNot parsed.`;
    return [
      `WAD File: ${this.filePath}`,
      `Type: ${header.identification}`,
      `Directory Entries: ${this.directoryEntries.length}`,
      "",
      "Data Structures:",
      `- Vertexes: ${this.vertexes.length}`,
      `- LineDefs: ${this.lineDefs.length}`,
      `- SideDefs: ${this.sideDefs.length}`,
      `- Sectors: ${this.sectors.length}`,
      `- Things: ${this.things.length}`,
      `- Nodes: ${this.nodes.length}`,
      `- SubSectors: ${this.subSectors.length}`,
      `- Segs: ${this.segs.length}`,
    ].join("\n");
  }

  private readHeader(buffer: Buffer): void {
    const identification = ascii(buffer, 0, 4);
    const numLumps = buffer.readInt32LE(4);
    const infoTableOffset = buffer.readInt32LE(8);
    this.wadHeader = { identification, numLumps, infoTableOffset };
  }

  private readDirectory(buffer: Buffer): void {
    const header = this.wadHeader;
    if (!header) throw new Error("Missing WAD header.");
    this.directoryEntries = [];
    for (let i = 0; i < header.numLumps; i += 1) {
      const base = header.infoTableOffset + i * 16;
      const filePos = buffer.readInt32LE(base);
      const size = buffer.readInt32LE(base + 4);
      const name = ascii(buffer, base + 8, 8).trim();
      this.directoryEntries.push({ filePos, size, name });
    }
  }

  private findEntry(name: string): DirectoryEntry | null {
    const lower = name.toLowerCase();
    return this.directoryEntries.find((entry) => entry.name.toLowerCase() === lower) ?? null;
  }

  private parseVertexes(buffer: Buffer): void {
    const entry = this.findEntry("VERTEXES");
    if (!entry) return;
    const count = Math.floor(entry.size / 4);
    this.vertexes = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 4;
      this.vertexes.push({ x: buffer.readInt16LE(base), y: buffer.readInt16LE(base + 2) });
    }
  }

  private parseLineDefs(buffer: Buffer): void {
    const entry = this.findEntry("LINEDEFS");
    if (!entry) return;
    const count = Math.floor(entry.size / 14);
    this.lineDefs = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 14;
      this.lineDefs.push({
        startVertex: buffer.readUInt16LE(base),
        endVertex: buffer.readUInt16LE(base + 2),
        flags: buffer.readInt16LE(base + 4),
        specialType: buffer.readInt16LE(base + 6),
        sectorTag: buffer.readInt16LE(base + 8),
        rightSideDef: buffer.readUInt16LE(base + 10),
        leftSideDef: buffer.readUInt16LE(base + 12),
      });
    }
  }

  private parseSideDefs(buffer: Buffer): void {
    const entry = this.findEntry("SIDEDEFS");
    if (!entry) return;
    const count = Math.floor(entry.size / 30);
    this.sideDefs = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 30;
      this.sideDefs.push({
        xOffset: buffer.readInt16LE(base),
        yOffset: buffer.readInt16LE(base + 2),
        upperTexture: ascii(buffer, base + 4, 8).trim(),
        lowerTexture: ascii(buffer, base + 12, 8).trim(),
        middleTexture: ascii(buffer, base + 20, 8).trim(),
        sector: buffer.readUInt16LE(base + 28),
      });
    }
  }

  private parseSectors(buffer: Buffer): void {
    const entry = this.findEntry("SECTORS");
    if (!entry) return;
    const count = Math.floor(entry.size / 26);
    this.sectors = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 26;
      this.sectors.push({
        floorHeight: buffer.readInt16LE(base),
        ceilingHeight: buffer.readInt16LE(base + 2),
        floorTexture: ascii(buffer, base + 4, 8).trim(),
        ceilingTexture: ascii(buffer, base + 12, 8).trim(),
        lightLevel: buffer.readInt16LE(base + 20),
        specialType: buffer.readInt16LE(base + 22),
        tag: buffer.readInt16LE(base + 24),
      });
    }
  }

  private parseThings(buffer: Buffer): void {
    const entry = this.findEntry("THINGS");
    if (!entry) return;
    const count = Math.floor(entry.size / 10);
    this.things = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 10;
      this.things.push({
        x: buffer.readInt16LE(base),
        y: buffer.readInt16LE(base + 2),
        angle: buffer.readInt16LE(base + 4),
        type: buffer.readInt16LE(base + 6),
        flags: buffer.readInt16LE(base + 8),
      });
    }
  }

  private parseNodes(buffer: Buffer): void {
    const entry = this.findEntry("NODES");
    if (!entry) return;
    const count = Math.floor(entry.size / 28);
    this.nodes = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 28;
      this.nodes.push({
        xPartition: buffer.readInt16LE(base),
        yPartition: buffer.readInt16LE(base + 2),
        xChange: buffer.readInt16LE(base + 4),
        yChange: buffer.readInt16LE(base + 6),
        rightBoxTop: buffer.readInt16LE(base + 8),
        rightBoxBottom: buffer.readInt16LE(base + 10),
        rightBoxLeft: buffer.readInt16LE(base + 12),
        rightBoxRight: buffer.readInt16LE(base + 14),
        leftBoxTop: buffer.readInt16LE(base + 16),
        leftBoxBottom: buffer.readInt16LE(base + 18),
        leftBoxLeft: buffer.readInt16LE(base + 20),
        leftBoxRight: buffer.readInt16LE(base + 22),
        rightChild: buffer.readUInt16LE(base + 24),
        leftChild: buffer.readUInt16LE(base + 26),
      });
    }
  }

  private parseSubSectors(buffer: Buffer): void {
    const entry = this.findEntry("SSECTORS");
    if (!entry) return;
    const count = Math.floor(entry.size / 4);
    this.subSectors = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 4;
      this.subSectors.push({
        segCount: buffer.readInt16LE(base),
        firstSeg: buffer.readUInt16LE(base + 2),
      });
    }
  }

  private parseSegs(buffer: Buffer): void {
    const entry = this.findEntry("SEGS");
    if (!entry) return;
    const count = Math.floor(entry.size / 12);
    this.segs = [];
    for (let i = 0; i < count; i += 1) {
      const base = entry.filePos + i * 12;
      this.segs.push({
        startVertex: buffer.readUInt16LE(base),
        endVertex: buffer.readUInt16LE(base + 2),
        angle: buffer.readInt16LE(base + 4),
        lineDef: buffer.readUInt16LE(base + 6),
        direction: buffer.readInt16LE(base + 8),
        offset: buffer.readInt16LE(base + 10),
      });
    }
  }
}

function ascii(buffer: Buffer, offset: number, length: number): string {
  return buffer
    .subarray(offset, offset + length)
    .toString("ascii")
    .replace(/\0/g, "")
    .trim();
}
