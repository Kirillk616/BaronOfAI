import type { BuiltLevel } from "../wad/levelBuilder.js";
import type { LineDef, MapAnnotation, Node, Sector, Seg, SideDef, SubSector, Thing, Vertex } from "../wad/wadModels.js";
import type { ConnectionPlan, MapPlan, RoomNode, RoomPlan } from "./mapPlanModels.js";

const PLACEMENT_GRID = 64;
const MIN_PORTAL_OVERLAP = 96;
const SUBSECTOR_FLAG = 0x8000;

export class MapPlanCompiler {
  compile(plan: MapPlan): BuiltLevel {
    if (plan.rooms.length === 0) {
      throw new Error("MapPlan has no room layouts to compile.");
    }

    const roomLookup = new Map(plan.rooms.map((room) => [room.id, room]));
    const placements = this.placeRooms(plan, roomLookup);
    const sectors: Sector[] = plan.rooms.map((room) => ({
      floorHeight: room.floorHeight,
      ceilingHeight: room.ceilingHeight,
      floorTexture: wadName(room.floorTexture),
      ceilingTexture: wadName(room.ceilingTexture),
      lightLevel: room.light,
      specialType: 0,
      tag: 0,
    }));
    const sectorIndexByRoom = new Map<string, number>(plan.rooms.map((room, index) => [room.id, index]));

    const builder = new GeometryBuilder();
    const portals = this.buildPortals(plan, placements, sectorIndexByRoom);
    const portalIntervals = new Map<string, IntRange[]>();

    portals.forEach((portal) => {
      this.pushPortalInterval(portalIntervals, portal.fromRoom, portal.fromSide, portal.interval);
      this.pushPortalInterval(portalIntervals, portal.toRoom, portal.toSide, portal.interval);
      builder.addPortal(portal);
    });

    plan.rooms.forEach((room) => {
      const rect = mustGet(placements, room.id, "room placement");
      const sectorIndex = mustGet(sectorIndexByRoom, room.id, "sector index");
      builder.addRoomWalls(room, rect, sectorIndex, portalIntervals);
    });

    const things = this.buildThings(plan, placements);
    const bsp = new SimpleBspBuilder(builder.lineDefs, builder.sideDefs, builder.vertexes, plan.rooms.map((room, index) => ({
      sectorIndex: index,
      rect: mustGet(placements, room.id, "room placement"),
    }))).build();

    return {
      vertexes: builder.vertexes,
      lineDefs: builder.lineDefs,
      sideDefs: builder.sideDefs,
      sectors,
      things,
      nodes: bsp.nodes,
      subSectors: bsp.subSectors,
      segs: bsp.segs,
      annotations: this.buildRoomAnnotations(plan, placements),
    };
  }

  private pushPortalInterval(map: Map<string, IntRange[]>, roomId: string, side: Side, interval: IntRange): void {
    const key = roomSideKey(roomId, side);
    const current = map.get(key) ?? [];
    current.push(interval);
    map.set(key, current);
  }

  private placeRooms(plan: MapPlan, roomLookup: Map<string, RoomPlan>): Map<string, Rect> {
    const startId = plan.topology.find((node) => node.role === "start")?.id ?? plan.rooms[0].id;
    const startRoom = mustGet(roomLookup, startId, "start room");
    const placed = new Map<string, Rect>([
      [startId, new Rect(0, 0, grid(startRoom.width), grid(startRoom.height))],
    ]);
    const directionsByRoom = new Map<string, number>();

    let changed: boolean;
    do {
      changed = false;
      plan.connections.forEach((connection) => {
        if (placed.has(connection.from) && !placed.has(connection.to)) {
          const target = roomLookup.get(connection.to);
          if (!target) return;
          const source = mustGet(placed, connection.from, "source placement");
          const next = this.findPlacement(
            source,
            target,
            this.preferredDirections(target, connection, directionsByRoom.get(connection.from) ?? 0),
            Array.from(placed.values()),
          );
          placed.set(connection.to, next);
          directionsByRoom.set(connection.from, (directionsByRoom.get(connection.from) ?? 0) + 1);
          changed = true;
        }

        if (placed.has(connection.to) && !placed.has(connection.from)) {
          const target = roomLookup.get(connection.from);
          if (!target) return;
          const source = mustGet(placed, connection.to, "source placement");
          const next = this.findPlacement(
            source,
            target,
            this.preferredDirections(target, connection, directionsByRoom.get(connection.to) ?? 0),
            Array.from(placed.values()),
          );
          placed.set(connection.from, next);
          directionsByRoom.set(connection.to, (directionsByRoom.get(connection.to) ?? 0) + 1);
          changed = true;
        }
      });
    } while (changed);

    plan.rooms
      .filter((room) => !placed.has(room.id))
      .forEach((room, index) => {
        const values = Array.from(placed.values());
        const previous = values[values.length - 1];
        placed.set(
          room.id,
          new Rect(previous.right, index * 768, previous.right + grid(room.width), index * 768 + grid(room.height)),
        );
      });

    return placed;
  }

  private preferredDirections(room: RoomPlan, connection: ConnectionPlan, usedDirections: number): Direction[] {
    if (connection.kind === "secret_door") {
      return [Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST];
    }
    if (room.id.endsWith("_key")) {
      return usedDirections % 2 === 0
        ? [Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST]
        : [Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST];
    }
    if (roleLikeExit(room) || room.id.endsWith("_lock")) {
      return [Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.WEST];
    }
    return [Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.WEST];
  }

  private findPlacement(source: Rect, target: RoomPlan, preferred: Direction[], placed: Rect[]): Rect {
    const width = grid(target.width);
    const height = grid(target.height);
    const directions = [...preferred, ...Object.values(Direction).filter((value) => !preferred.includes(value))];
    const candidates: Rect[] = [];

    directions.forEach((direction) => {
      this.placementOffsets().forEach((offset) => {
        candidates.push(this.touchingRect(source, width, height, direction, offset));
      });
    });

    const found = candidates.find((candidate) =>
      candidate.touchOverlap(source) >= MIN_PORTAL_OVERLAP && placed.every((rect) => !rect.overlaps(candidate)),
    );
    if (!found) {
      throw new Error(`Could not place room ${target.id} next to its source without overlap. Increase hub size or add connector rooms.`);
    }
    return found;
  }

  private placementOffsets(): number[] {
    const values = [0];
    for (let step = 1; step <= 18; step += 1) {
      values.push(-step, step);
    }
    return values;
  }

  private touchingRect(source: Rect, width: number, height: number, direction: Direction, offset: number): Rect {
    const shift = offset * PLACEMENT_GRID;
    switch (direction) {
      case Direction.EAST: {
        const bottom = source.centerY - Math.floor(height / 2) + shift;
        return new Rect(source.right, bottom, source.right + width, bottom + height);
      }
      case Direction.WEST: {
        const bottom = source.centerY - Math.floor(height / 2) + shift;
        return new Rect(source.left - width, bottom, source.left, bottom + height);
      }
      case Direction.NORTH: {
        const left = source.centerX - Math.floor(width / 2) + shift;
        return new Rect(left, source.top, left + width, source.top + height);
      }
      case Direction.SOUTH: {
        const left = source.centerX - Math.floor(width / 2) + shift;
        return new Rect(left, source.bottom - height, left + width, source.bottom);
      }
      default:
        return new Rect(0, 0, width, height);
    }
  }

  private buildPortals(plan: MapPlan, placements: Map<string, Rect>, sectorIndexByRoom: Map<string, number>): Portal[] {
    const portals: Portal[] = [];
    const seenPairs = new Set<string>();

    plan.connections.forEach((connection) => {
      const from = placements.get(connection.from);
      const to = placements.get(connection.to);
      if (!from || !to) return;

      const pair = [connection.from, connection.to].sort().join("::");
      if (seenPairs.has(pair)) return;
      seenPairs.add(pair);

      const portal = this.touchingPortal(
        connection.from,
        from,
        mustGet(sectorIndexByRoom, connection.from, "from sector"),
        connection.to,
        to,
        mustGet(sectorIndexByRoom, connection.to, "to sector"),
        connection,
      );
      if (portal) portals.push(portal);
    });

    return portals;
  }

  private touchingPortal(
    fromRoom: string,
    fromRect: Rect,
    fromSector: number,
    toRoom: string,
    toRect: Rect,
    toSector: number,
    connection: ConnectionPlan,
  ): Portal | null {
    if (fromRect.right === toRect.left) {
      return this.verticalPortal(fromRoom, Side.EAST, fromSector, toRoom, Side.WEST, toSector, fromRect.right, fromRect, toRect, connection);
    }
    if (fromRect.left === toRect.right) {
      return this.verticalPortal(fromRoom, Side.WEST, fromSector, toRoom, Side.EAST, toSector, fromRect.left, fromRect, toRect, connection);
    }
    if (fromRect.top === toRect.bottom) {
      return this.horizontalPortal(fromRoom, Side.NORTH, fromSector, toRoom, Side.SOUTH, toSector, fromRect.top, fromRect, toRect, connection);
    }
    if (fromRect.bottom === toRect.top) {
      return this.horizontalPortal(fromRoom, Side.SOUTH, fromSector, toRoom, Side.NORTH, toSector, fromRect.bottom, fromRect, toRect, connection);
    }
    return null;
  }

  private verticalPortal(
    fromRoom: string,
    fromSide: Side,
    fromSector: number,
    toRoom: string,
    toSide: Side,
    toSector: number,
    x: number,
    fromRect: Rect,
    toRect: Rect,
    connection: ConnectionPlan,
  ): Portal | null {
    const overlap = range(Math.max(fromRect.bottom, toRect.bottom), Math.min(fromRect.top, toRect.top));
    if (overlap.last - overlap.first < 96) return null;
    const interval = this.centeredInterval(overlap, this.widthFor(connection));
    const startY = fromSide === Side.EAST ? interval.last : interval.first;
    const endY = fromSide === Side.EAST ? interval.first : interval.last;
    return new Portal(
      fromRoom,
      fromSide,
      fromSector,
      toRoom,
      toSide,
      toSector,
      new Point(x, startY),
      new Point(x, endY),
      interval,
      connection,
    );
  }

  private horizontalPortal(
    fromRoom: string,
    fromSide: Side,
    fromSector: number,
    toRoom: string,
    toSide: Side,
    toSector: number,
    y: number,
    fromRect: Rect,
    toRect: Rect,
    connection: ConnectionPlan,
  ): Portal | null {
    const overlap = range(Math.max(fromRect.left, toRect.left), Math.min(fromRect.right, toRect.right));
    if (overlap.last - overlap.first < 96) return null;
    const interval = this.centeredInterval(overlap, this.widthFor(connection));
    const startX = fromSide === Side.SOUTH ? interval.last : interval.first;
    const endX = fromSide === Side.SOUTH ? interval.first : interval.last;
    return new Portal(
      fromRoom,
      fromSide,
      fromSector,
      toRoom,
      toSide,
      toSector,
      new Point(startX, y),
      new Point(endX, y),
      interval,
      connection,
    );
  }

  private centeredInterval(overlap: IntRange, desiredWidth: number): IntRange {
    const available = overlap.last - overlap.first;
    const width = clamp(desiredWidth, 96, Math.max(96, available - 32));
    const center = Math.floor((overlap.first + overlap.last) / 2);
    return range(center - Math.floor(width / 2), center + Math.floor(width / 2));
  }

  private widthFor(connection: ConnectionPlan): number {
    switch (connection.kind) {
      case "secret_door":
        return 96;
      case "locked_door":
        return 128;
      default:
        return 160;
    }
  }

  private buildThings(plan: MapPlan, placements: Map<string, Rect>): Thing[] {
    const things: Thing[] = [];
    const lowerPrompt = plan.profile.originalPrompt.toLowerCase();
    const startRoomId = (
      ["center of the huge room", "center of room a", "inside in the center of the room"].some((text) => lowerPrompt.includes(text))
        ? plan.topology.find((node) => node.role === "arena")?.id
        : plan.topology.find((node) => node.role === "start")?.id
    ) ?? plan.rooms[0].id;

    const start = placements.get(startRoomId);
    if (start) {
      things.push({ x: start.centerX, y: start.centerY, angle: 0, type: 1, flags: 7 });
    }

    if (lowerPrompt.includes("imp")) {
      const impMatch = lowerPrompt.match(/\b([1-9])\s+imps?\b/);
      const impCount = impMatch?.[1] ? Number.parseInt(impMatch[1], 10) : 4;
      const arenaId = plan.topology.find((node) => node.role === "arena")?.id;
      const arena = arenaId ? placements.get(arenaId) : undefined;
      if (arena) {
        things.push(...this.cornerThings(arena, clamp(impCount, 1, 8), 3001));
      }
    }

    plan.topology.forEach((node) => {
      const rect = placements.get(node.id);
      if (!rect) return;
      if (node.grantsKey) {
        things.push({ x: rect.centerX, y: rect.centerY, angle: 0, type: this.keyThingType(node.grantsKey), flags: 7 });
      }
      if (node.role === "exit") {
        things.push({ x: rect.centerX + 48, y: rect.centerY, angle: 0, type: 2015, flags: 7 });
      }
    });

    return things;
  }

  private cornerThings(rect: Rect, count: number, type: number): Thing[] {
    const inset = 96;
    const corners = [
      new Point(rect.left + inset, rect.bottom + inset),
      new Point(rect.right - inset, rect.bottom + inset),
      new Point(rect.right - inset, rect.top - inset),
      new Point(rect.left + inset, rect.top - inset),
      new Point(rect.centerX, rect.top - inset),
      new Point(rect.right - inset, rect.centerY),
      new Point(rect.centerX, rect.bottom + inset),
      new Point(rect.left + inset, rect.centerY),
    ];
    return corners.slice(0, count).map((point) => ({ x: point.x, y: point.y, angle: 0, type, flags: 7 }));
  }

  private keyThingType(key: string): number {
    switch (key) {
      case "blue":
        return 5;
      case "yellow":
        return 6;
      case "red":
        return 13;
      default:
        return 5;
    }
  }

  private buildRoomAnnotations(plan: MapPlan, placements: Map<string, Rect>): MapAnnotation[] {
    const nodesById = new Map<string, RoomNode>(plan.topology.map((node) => [node.id, node]));
    const annotations: MapAnnotation[] = [];
    plan.rooms.forEach((room, index) => {
      const rect = placements.get(room.id);
      if (!rect) return;
      const node = nodesById.get(room.id);
      annotations.push({
        text: String(index + 1),
        x: rect.centerX,
        y: rect.centerY,
        title: [node?.label, node?.role ? `role=${node.role}` : undefined, `id=${room.id}`]
          .filter((v): v is string => Boolean(v))
          .join(" | "),
      });
    });
    return annotations;
  }
}

class GeometryBuilder {
  private vertexIndexByPoint = new Map<string, number>();
  vertexes: Vertex[] = [];
  sideDefs: SideDef[] = [];
  lineDefs: LineDef[] = [];

  addPortal(portal: Portal): void {
    const right = this.addSideDef(portal.fromSector, "-", "-", "-");
    const left = this.addSideDef(portal.toSector, portal.toTexture(), portal.toTexture(), "-");
    this.lineDefs.push({
      startVertex: this.vertexIndex(portal.start),
      endVertex: this.vertexIndex(portal.end),
      flags: 4,
      specialType: 0,
      sectorTag: 0,
      rightSideDef: right,
      leftSideDef: left,
    });
  }

  addRoomWalls(room: RoomPlan, rect: Rect, sectorIndex: number, blockedIntervals: Map<string, IntRange[]>): void {
    const wallTexture = wadName(room.wallTexture);
    this.addSideSegments(room.id, Side.SOUTH, rect.left, rect.right, rect.bottom, sectorIndex, wallTexture, blockedIntervals);
    this.addSideSegments(room.id, Side.EAST, rect.bottom, rect.top, rect.right, sectorIndex, wallTexture, blockedIntervals);
    this.addSideSegments(room.id, Side.NORTH, rect.left, rect.right, rect.top, sectorIndex, wallTexture, blockedIntervals);
    this.addSideSegments(room.id, Side.WEST, rect.bottom, rect.top, rect.left, sectorIndex, wallTexture, blockedIntervals);
  }

  private addSideSegments(
    roomId: string,
    side: Side,
    start: number,
    end: number,
    fixed: number,
    sectorIndex: number,
    wallTexture: string,
    blockedIntervals: Map<string, IntRange[]>,
  ): void {
    const intervals = blockedIntervals.get(roomSideKey(roomId, side)) ?? [];
    const openSegments = this.subtractIntervals(range(start, end), intervals);
    openSegments.forEach((segment) => {
      if (segment.last - segment.first < 8) return;
      const lineStart = this.startPointFor(side, segment, fixed);
      const lineEnd = this.endPointFor(side, segment, fixed);
      const sideDef = this.addSideDef(sectorIndex, "-", "-", wallTexture);
      this.lineDefs.push({
        startVertex: this.vertexIndex(lineStart),
        endVertex: this.vertexIndex(lineEnd),
        flags: 1,
        specialType: 0,
        sectorTag: 0,
        rightSideDef: sideDef,
        leftSideDef: -1,
      });
    });
  }

  private startPointFor(side: Side, segment: IntRange, fixed: number): Point {
    switch (side) {
      case Side.SOUTH:
        return new Point(segment.last, fixed);
      case Side.EAST:
        return new Point(fixed, segment.last);
      case Side.NORTH:
        return new Point(segment.first, fixed);
      case Side.WEST:
        return new Point(fixed, segment.first);
      default:
        return new Point(segment.first, fixed);
    }
  }

  private endPointFor(side: Side, segment: IntRange, fixed: number): Point {
    switch (side) {
      case Side.SOUTH:
        return new Point(segment.first, fixed);
      case Side.EAST:
        return new Point(fixed, segment.first);
      case Side.NORTH:
        return new Point(segment.last, fixed);
      case Side.WEST:
        return new Point(fixed, segment.last);
      default:
        return new Point(segment.last, fixed);
    }
  }

  private addSideDef(sectorIndex: number, upper: string, lower: string, middle: string): number {
    const index = this.sideDefs.length;
    this.sideDefs.push({
      xOffset: 0,
      yOffset: 0,
      upperTexture: wadName(upper),
      lowerTexture: wadName(lower),
      middleTexture: wadName(middle),
      sector: sectorIndex,
    });
    return index;
  }

  private vertexIndex(point: Point): number {
    const key = point.key();
    const existing = this.vertexIndexByPoint.get(key);
    if (existing !== undefined) return existing;
    const next = this.vertexes.length;
    this.vertexes.push({ x: point.x, y: point.y });
    this.vertexIndexByPoint.set(key, next);
    return next;
  }

  private subtractIntervals(bounds: IntRange, blocked: IntRange[]): IntRange[] {
    const normalized = blocked
      .map((interval) => range(Math.max(bounds.first, interval.first), Math.min(bounds.last, interval.last)))
      .filter((interval) => interval.first < interval.last)
      .sort((a, b) => a.first - b.first);

    const segments: IntRange[] = [];
    let cursor = bounds.first;
    normalized.forEach((interval) => {
      if (cursor < interval.first) segments.push(range(cursor, interval.first));
      cursor = Math.max(cursor, interval.last);
    });
    if (cursor < bounds.last) segments.push(range(cursor, bounds.last));
    return segments;
  }
}

class SimpleBspBuilder {
  private readonly lineDefs: LineDef[];
  private readonly sideDefs: SideDef[];
  private readonly vertexes: Vertex[];
  private readonly sectors: BspSector[];
  private nodes: Node[] = [];

  constructor(lineDefs: LineDef[], sideDefs: SideDef[], vertexes: Vertex[], sectorRects: BspSector[]) {
    this.lineDefs = lineDefs;
    this.sideDefs = sideDefs;
    this.vertexes = vertexes;
    this.sectors = sectorRects;
  }

  build(): BspLumps {
    const segs: Seg[] = [];
    const subSectors: SubSector[] = [];

    this.sectors
      .slice()
      .sort((a, b) => a.sectorIndex - b.sectorIndex)
      .forEach((sector) => {
        const firstSeg = segs.length;
        this.lineDefs.forEach((lineDef, lineIndex) => {
          const rightSector = this.sideDefs[lineDef.rightSideDef]?.sector;
          if (rightSector === sector.sectorIndex) {
            segs.push(this.segFor(lineDef, lineIndex, 0));
          }
          const leftSector = lineDef.leftSideDef >= 0 ? this.sideDefs[lineDef.leftSideDef]?.sector : undefined;
          if (leftSector === sector.sectorIndex) {
            segs.push(this.segFor(lineDef, lineIndex, 1));
          }
        });
        subSectors.push({
          segCount: segs.length - firstSeg,
          firstSeg,
        });
      });

    if (this.sectors.length > 1) {
      this.buildNode(this.sectors);
    }

    return {
      nodes: this.nodes,
      subSectors,
      segs,
    };
  }

  private buildNode(entries: BspSector[]): number {
    if (entries.length === 1) return SUBSECTOR_FLAG | entries[0].sectorIndex;

    const bounds = this.boundsFor(entries);
    const splitVertical = bounds.right - bounds.left >= bounds.top - bounds.bottom;
    const sorted = entries
      .slice()
      .sort((a, b) =>
        splitVertical ? a.rect.centerX - b.rect.centerX : a.rect.centerY - b.rect.centerY,
      );
    const leftEntries = sorted.slice(0, Math.floor(sorted.length / 2));
    const rightEntries = sorted.slice(Math.floor(sorted.length / 2));

    const leftChild = this.buildNode(leftEntries);
    const rightChild = this.buildNode(rightEntries);
    const leftBox = this.boundsFor(leftEntries);
    const rightBox = this.boundsFor(rightEntries);

    let node: Node;
    if (splitVertical) {
      const splitX = Math.floor((leftBox.right + rightBox.left) / 2);
      node = {
        xPartition: splitX,
        yPartition: bounds.bottom,
        xChange: 0,
        yChange: bounds.top - bounds.bottom,
        rightBoxTop: rightBox.top,
        rightBoxBottom: rightBox.bottom,
        rightBoxLeft: rightBox.left,
        rightBoxRight: rightBox.right,
        leftBoxTop: leftBox.top,
        leftBoxBottom: leftBox.bottom,
        leftBoxLeft: leftBox.left,
        leftBoxRight: leftBox.right,
        rightChild,
        leftChild,
      };
    } else {
      const splitY = Math.floor((leftBox.top + rightBox.bottom) / 2);
      node = {
        xPartition: bounds.left,
        yPartition: splitY,
        xChange: bounds.right - bounds.left,
        yChange: 0,
        rightBoxTop: rightBox.top,
        rightBoxBottom: rightBox.bottom,
        rightBoxLeft: rightBox.left,
        rightBoxRight: rightBox.right,
        leftBoxTop: leftBox.top,
        leftBoxBottom: leftBox.bottom,
        leftBoxLeft: leftBox.left,
        leftBoxRight: leftBox.right,
        rightChild,
        leftChild,
      };
    }

    this.nodes.push(node);
    return this.nodes.length - 1;
  }

  private segFor(lineDef: LineDef, lineIndex: number, direction: number): Seg {
    const startIndex = direction === 0 ? lineDef.startVertex : lineDef.endVertex;
    const endIndex = direction === 0 ? lineDef.endVertex : lineDef.startVertex;
    const start = this.vertexes[startIndex];
    const end = this.vertexes[endIndex];
    return {
      startVertex: startIndex,
      endVertex: endIndex,
      angle: this.doomAngle(start, end),
      lineDef: lineIndex,
      direction,
      offset: 0,
    };
  }

  private doomAngle(start: Vertex, end: Vertex): number {
    const radians = Math.atan2(end.y - start.y, end.x - start.x);
    const turns = radians / (Math.PI * 2.0);
    return (Math.trunc(turns * 65536.0) & 0xffff) >>> 0;
  }

  private boundsFor(entries: BspSector[]): Rect {
    return new Rect(
      Math.min(...entries.map((entry) => entry.rect.left)),
      Math.min(...entries.map((entry) => entry.rect.bottom)),
      Math.max(...entries.map((entry) => entry.rect.right)),
      Math.max(...entries.map((entry) => entry.rect.top)),
    );
  }
}

interface BspLumps {
  nodes: Node[];
  subSectors: SubSector[];
  segs: Seg[];
}

interface BspSector {
  sectorIndex: number;
  rect: Rect;
}

interface IntRange {
  first: number;
  last: number;
}

class Rect {
  left: number;
  bottom: number;
  right: number;
  top: number;

  constructor(left: number, bottom: number, right: number, top: number) {
    this.left = left;
    this.bottom = bottom;
    this.right = right;
    this.top = top;
  }

  get centerX(): number {
    return Math.floor((this.left + this.right) / 2);
  }

  get centerY(): number {
    return Math.floor((this.bottom + this.top) / 2);
  }

  overlaps(other: Rect): boolean {
    return this.left < other.right && this.right > other.left && this.bottom < other.top && this.top > other.bottom;
  }

  touchOverlap(other: Rect): number {
    if (this.right === other.left || this.left === other.right) {
      return Math.min(this.top, other.top) - Math.max(this.bottom, other.bottom);
    }
    if (this.top === other.bottom || this.bottom === other.top) {
      return Math.min(this.right, other.right) - Math.max(this.left, other.left);
    }
    return 0;
  }
}

class Point {
  x: number;
  y: number;

  constructor(x: number, y: number) {
    this.x = x;
    this.y = y;
  }

  key(): string {
    return `${this.x}:${this.y}`;
  }
}

class Portal {
  fromRoom: string;
  fromSide: Side;
  fromSector: number;
  toRoom: string;
  toSide: Side;
  toSector: number;
  start: Point;
  end: Point;
  interval: IntRange;
  connection: ConnectionPlan;

  constructor(
    fromRoom: string,
    fromSide: Side,
    fromSector: number,
    toRoom: string,
    toSide: Side,
    toSector: number,
    start: Point,
    end: Point,
    interval: IntRange,
    connection: ConnectionPlan,
  ) {
    this.fromRoom = fromRoom;
    this.fromSide = fromSide;
    this.fromSector = fromSector;
    this.toRoom = toRoom;
    this.toSide = toSide;
    this.toSector = toSector;
    this.start = start;
    this.end = end;
    this.interval = interval;
    this.connection = connection;
  }

  toTexture(): string {
    switch (this.connection.requiredKey) {
      case "blue":
        return "DOORBLU";
      case "red":
        return "DOORRED";
      case "yellow":
        return "DOORYEL";
      default:
        return "STARTAN2";
    }
  }
}

enum Direction {
  EAST = "EAST",
  NORTH = "NORTH",
  SOUTH = "SOUTH",
  WEST = "WEST",
}

enum Side {
  SOUTH = "SOUTH",
  EAST = "EAST",
  NORTH = "NORTH",
  WEST = "WEST",
}

function roleLikeExit(room: RoomPlan): boolean {
  return room.id === "exit" || room.encounterHint.toLowerCase().includes("final");
}

function grid(value: number): number {
  const v = Math.max(value, 160);
  return Math.floor((v + 31) / 32) * 32;
}

function wadName(value: string): string {
  return value === "-" ? "-" : value.slice(0, 8);
}

function roomSideKey(roomId: string, side: Side): string {
  return `${roomId}:${side}`;
}

function range(first: number, last: number): IntRange {
  return { first, last };
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function mustGet<K, V>(map: Map<K, V>, key: K, label: string): V {
  const value = map.get(key);
  if (value === undefined) {
    throw new Error(`Missing ${label} for key: ${String(key)}`);
  }
  return value;
}
