import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import type { BuiltLevel } from "./levelBuilder.js";
import type { LineDef, Node, Sector, Seg, SideDef, SubSector, Thing, Vertex } from "./wadModels.js";

interface DirectoryEntry {
  filePos: number;
  size: number;
  name: string;
}

export class WadWriter {
  private readonly outputPath: string;
  private readonly identification = "PWAD";
  private readonly directoryEntries: DirectoryEntry[] = [];
  private chunks: Buffer[] = [];
  private offset = 0;

  constructor(outputPath: string) {
    this.outputPath = outputPath;
  }

  writeLevel(level: BuiltLevel): boolean {
    return this.write(
      level.vertexes,
      level.lineDefs,
      level.sideDefs,
      level.sectors,
      level.things,
      level.nodes,
      level.subSectors,
      level.segs,
    );
  }

  write(
    vertexes: Vertex[],
    lineDefs: LineDef[],
    sideDefs: SideDef[],
    sectors: Sector[],
    things: Thing[],
    nodes: Node[],
    subSectors: SubSector[],
    segs: Seg[],
  ): boolean {
    try {
      this.directoryEntries.length = 0;
      this.chunks = [];
      this.offset = 0;

      this.push(Buffer.alloc(12)); // header placeholder
      const mapMarkerPos = this.offset;
      this.addDirectoryEntry("MAP01", mapMarkerPos, 0);

      const ensuredThings = things.some((thing) => thing.type === 1)
        ? things
        : [...things, { x: 0, y: 0, angle: 0, type: 1, flags: 7 }];

      this.writeThings(ensuredThings);
      this.writeLineDefs(lineDefs);
      this.writeSideDefs(sideDefs);
      this.writeVertexes(vertexes);
      this.writeSegs(segs);
      this.writeSubSectors(subSectors);
      this.writeNodes(nodes);
      this.writeSectors(sectors);
      this.writeReject(sectors.length);
      this.writeBlockMap(vertexes, lineDefs);

      const directoryOffset = this.offset;
      this.writeDirectory();
      this.writeHeader(directoryOffset);

      mkdirSync(dirname(this.outputPath), { recursive: true });
      writeFileSync(this.outputPath, Buffer.concat(this.chunks));
      return true;
    } catch {
      return false;
    }
  }

  private writeHeader(directoryOffset: number): void {
    const header = Buffer.alloc(12);
    writeAsciiFixed(header, 0, this.identification, 4);
    header.writeInt32LE(this.directoryEntries.length, 4);
    header.writeInt32LE(directoryOffset, 8);
    this.chunks[0] = header;
  }

  private writeDirectory(): void {
    const directory = Buffer.alloc(this.directoryEntries.length * 16);
    this.directoryEntries.forEach((entry, index) => {
      const base = index * 16;
      directory.writeInt32LE(entry.filePos, base);
      directory.writeInt32LE(entry.size, base + 4);
      writeAsciiFixed(directory, base + 8, entry.name, 8);
    });
    this.push(directory);
  }

  private addDirectoryEntry(name: string, filePos: number, size: number): void {
    this.directoryEntries.push({ name, filePos, size });
  }

  private writeVertexes(vertexes: Vertex[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(vertexes.length * 4);
    vertexes.forEach((vertex, index) => {
      const base = index * 4;
      buffer.writeInt16LE(toInt16(vertex.x), base);
      buffer.writeInt16LE(toInt16(vertex.y), base + 2);
    });
    this.push(buffer);
    this.addDirectoryEntry("VERTEXES", filePos, buffer.length);
  }

  private writeLineDefs(lineDefs: LineDef[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(lineDefs.length * 14);
    lineDefs.forEach((lineDef, index) => {
      const base = index * 14;
      buffer.writeInt16LE(toInt16(lineDef.startVertex), base);
      buffer.writeInt16LE(toInt16(lineDef.endVertex), base + 2);
      buffer.writeInt16LE(toInt16(lineDef.flags), base + 4);
      buffer.writeInt16LE(toInt16(lineDef.specialType), base + 6);
      buffer.writeInt16LE(toInt16(lineDef.sectorTag), base + 8);
      buffer.writeInt16LE(toInt16(lineDef.rightSideDef), base + 10);
      buffer.writeInt16LE(toInt16(lineDef.leftSideDef), base + 12);
    });
    this.push(buffer);
    this.addDirectoryEntry("LINEDEFS", filePos, buffer.length);
  }

  private writeSideDefs(sideDefs: SideDef[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(sideDefs.length * 30);
    sideDefs.forEach((sideDef, index) => {
      const base = index * 30;
      buffer.writeInt16LE(toInt16(sideDef.xOffset), base);
      buffer.writeInt16LE(toInt16(sideDef.yOffset), base + 2);
      writeAsciiFixed(buffer, base + 4, sideDef.upperTexture, 8);
      writeAsciiFixed(buffer, base + 12, sideDef.lowerTexture, 8);
      writeAsciiFixed(buffer, base + 20, sideDef.middleTexture, 8);
      buffer.writeInt16LE(toInt16(sideDef.sector), base + 28);
    });
    this.push(buffer);
    this.addDirectoryEntry("SIDEDEFS", filePos, buffer.length);
  }

  private writeSectors(sectors: Sector[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(sectors.length * 26);
    sectors.forEach((sector, index) => {
      const base = index * 26;
      buffer.writeInt16LE(toInt16(sector.floorHeight), base);
      buffer.writeInt16LE(toInt16(sector.ceilingHeight), base + 2);
      writeAsciiFixed(buffer, base + 4, sector.floorTexture, 8);
      writeAsciiFixed(buffer, base + 12, sector.ceilingTexture, 8);
      buffer.writeInt16LE(toInt16(sector.lightLevel), base + 20);
      buffer.writeInt16LE(toInt16(sector.specialType), base + 22);
      buffer.writeInt16LE(toInt16(sector.tag), base + 24);
    });
    this.push(buffer);
    this.addDirectoryEntry("SECTORS", filePos, buffer.length);
  }

  private writeReject(sectorCount: number): void {
    const filePos = this.offset;
    const totalSize = Math.floor((sectorCount * sectorCount + 7) / 8);
    const buffer = Buffer.alloc(totalSize);
    this.push(buffer);
    this.addDirectoryEntry("REJECT", filePos, totalSize);
  }

  private writeBlockMap(vertexes: Vertex[], lineDefs: LineDef[]): void {
    const filePos = this.offset;
    const blockSize = 128;
    const xs = vertexes.map((vertex) => vertex.x);
    const ys = vertexes.map((vertex) => vertex.y);
    const minX = xs.length > 0 ? Math.min(...xs) : 0;
    const minY = ys.length > 0 ? Math.min(...ys) : 0;
    const maxX = xs.length > 0 ? Math.max(...xs) : 0;
    const maxY = ys.length > 0 ? Math.max(...ys) : 0;
    const originX = Math.floor(minX / blockSize) * blockSize;
    const originY = Math.floor(minY / blockSize) * blockSize;
    const columns = Math.max(1, Math.floor((maxX - originX) / blockSize) + 1);
    const rows = Math.max(1, Math.floor((maxY - originY) / blockSize) + 1);
    const blockCount = columns * rows;
    const sharedListOffset = 4 + blockCount;
    const listWordCount = lineDefs.length + 1;
    const totalWords = sharedListOffset + listWordCount;
    const buffer = Buffer.alloc(totalWords * 2);

    let cursor = 0;
    cursor = writeWord(buffer, cursor, originX);
    cursor = writeWord(buffer, cursor, originY);
    cursor = writeWord(buffer, cursor, columns);
    cursor = writeWord(buffer, cursor, rows);
    for (let i = 0; i < blockCount; i += 1) {
      cursor = writeWord(buffer, cursor, sharedListOffset);
    }
    for (let index = 0; index < lineDefs.length; index += 1) {
      cursor = writeWord(buffer, cursor, index);
    }
    writeWord(buffer, cursor, -1);

    this.push(buffer);
    this.addDirectoryEntry("BLOCKMAP", filePos, buffer.length);
  }

  private writeThings(things: Thing[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(things.length * 10);
    things.forEach((thing, index) => {
      const base = index * 10;
      buffer.writeInt16LE(toInt16(thing.x), base);
      buffer.writeInt16LE(toInt16(thing.y), base + 2);
      buffer.writeInt16LE(toInt16(thing.angle), base + 4);
      buffer.writeInt16LE(toInt16(thing.type), base + 6);
      buffer.writeInt16LE(toInt16(thing.flags), base + 8);
    });
    this.push(buffer);
    this.addDirectoryEntry("THINGS", filePos, buffer.length);
  }

  private writeNodes(nodes: Node[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(nodes.length * 28);
    nodes.forEach((node, index) => {
      const base = index * 28;
      buffer.writeInt16LE(toInt16(node.xPartition), base);
      buffer.writeInt16LE(toInt16(node.yPartition), base + 2);
      buffer.writeInt16LE(toInt16(node.xChange), base + 4);
      buffer.writeInt16LE(toInt16(node.yChange), base + 6);
      buffer.writeInt16LE(toInt16(node.rightBoxTop), base + 8);
      buffer.writeInt16LE(toInt16(node.rightBoxBottom), base + 10);
      buffer.writeInt16LE(toInt16(node.rightBoxLeft), base + 12);
      buffer.writeInt16LE(toInt16(node.rightBoxRight), base + 14);
      buffer.writeInt16LE(toInt16(node.leftBoxTop), base + 16);
      buffer.writeInt16LE(toInt16(node.leftBoxBottom), base + 18);
      buffer.writeInt16LE(toInt16(node.leftBoxLeft), base + 20);
      buffer.writeInt16LE(toInt16(node.leftBoxRight), base + 22);
      buffer.writeInt16LE(toInt16(node.rightChild), base + 24);
      buffer.writeInt16LE(toInt16(node.leftChild), base + 26);
    });
    this.push(buffer);
    this.addDirectoryEntry("NODES", filePos, buffer.length);
  }

  private writeSubSectors(subSectors: SubSector[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(subSectors.length * 4);
    subSectors.forEach((subSector, index) => {
      const base = index * 4;
      buffer.writeInt16LE(toInt16(subSector.segCount), base);
      buffer.writeInt16LE(toInt16(subSector.firstSeg), base + 2);
    });
    this.push(buffer);
    this.addDirectoryEntry("SSECTORS", filePos, buffer.length);
  }

  private writeSegs(segs: Seg[]): void {
    const filePos = this.offset;
    const buffer = Buffer.alloc(segs.length * 12);
    segs.forEach((seg, index) => {
      const base = index * 12;
      buffer.writeInt16LE(toInt16(seg.startVertex), base);
      buffer.writeInt16LE(toInt16(seg.endVertex), base + 2);
      buffer.writeInt16LE(toInt16(seg.angle), base + 4);
      buffer.writeInt16LE(toInt16(seg.lineDef), base + 6);
      buffer.writeInt16LE(toInt16(seg.direction), base + 8);
      buffer.writeInt16LE(toInt16(seg.offset), base + 10);
    });
    this.push(buffer);
    this.addDirectoryEntry("SEGS", filePos, buffer.length);
  }

  private push(buffer: Buffer): void {
    this.chunks.push(buffer);
    this.offset += buffer.length;
  }
}

function writeWord(buffer: Buffer, cursor: number, value: number): number {
  buffer.writeInt16LE(toInt16(value), cursor);
  return cursor + 2;
}

function toInt16(value: number): number {
  return ((Math.trunc(value) + 0x8000) & 0xffff) - 0x8000;
}

function writeAsciiFixed(buffer: Buffer, offset: number, input: string, length: number): void {
  const src = Buffer.from(input.slice(0, length), "ascii");
  src.copy(buffer, offset, 0, Math.min(src.length, length));
  for (let i = src.length; i < length; i += 1) {
    buffer[offset + i] = 0;
  }
}
