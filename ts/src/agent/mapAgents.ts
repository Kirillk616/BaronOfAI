import {
  type ConnectionPlan,
  defaultValidationReport,
  type MapPlan,
  type MapPromptProfile,
  type RoomNode,
  type RoomPlan,
  type ValidationReport,
} from "./mapPlanModels.js";

class ThemeTextures {
  floor: string;
  ceiling: string;
  wall: string;

  constructor(floor: string, ceiling: string, wall: string) {
    this.floor = floor;
    this.ceiling = ceiling;
    this.wall = wall;
  }

  static forTheme(theme: string): ThemeTextures {
    switch (theme) {
      case "warehouse":
        return new ThemeTextures("FLOOR0_1", "CEIL1_1", "BROWN1");
      case "hell":
        return new ThemeTextures("LAVA1", "CEIL1_1", "SKINFACE");
      case "stone":
        return new ThemeTextures("FLOOR0_3", "CEIL3_5", "STONE2");
      default:
        return new ThemeTextures("FLOOR0_1", "CEIL1_1", "STARTAN2");
    }
  }
}

export class PromptProfileAgent {
  name = "prompt-profile-agent";

  run(input: string): MapPromptProfile {
    const prompt = input
      .split(/\r?\n/)
      .map((line) => line.trimEnd())
      .filter((line) => !line.trimStart().startsWith("#"))
      .join("\n")
      .trim();

    const lower = prompt.toLowerCase();
    const requestedKeys = ["blue", "red", "yellow"].filter(
      (key) => lower.includes(`${key} key`) || lower.includes(`${key} door`),
    );
    const requestedSetPieces: string[] = [];
    if (["bridge", "catwalk"].some((v) => lower.includes(v))) requestedSetPieces.push("bridge");
    if (["button", "switch"].some((v) => lower.includes(v))) requestedSetPieces.push("switch");
    if (["lava", "nukage", "acid", "blood"].some((v) => lower.includes(v))) requestedSetPieces.push("hazard-floor");
    if (["lift", "elevator", "platform"].some((v) => lower.includes(v))) requestedSetPieces.push("lift");
    if (["warehouse", "walmart", "store", "shelf", "shelves"].some((v) => lower.includes(v))) requestedSetPieces.push("aisle-arena");

    return {
      originalPrompt: prompt,
      title: this.inferTitle(lower),
      theme: this.inferTheme(lower),
      difficulty: this.inferDifficulty(lower),
      targetRoomCount: this.inferRoomCount(lower, requestedKeys),
      requestedKeys: requestedKeys.length > 0 ? requestedKeys : ["blue"],
      requestedSetPieces,
      lightingHint: ["dark", "dim", "gloom"].some((v) => lower.includes(v))
        ? "dark"
        : ["bright", "shiny", "office", "store"].some((v) => lower.includes(v))
          ? "bright"
          : "balanced",
    };
  }

  private inferTitle(lower: string): string {
    if (lower.includes("walmart") || lower.includes("store")) return "Retail Lockdown";
    if (lower.includes("hell") || lower.includes("flesh")) return "Furnace Prayer";
    if (lower.includes("base") || lower.includes("tech")) return "Relay Station";
    if (lower.includes("castle") || lower.includes("stone")) return "Broken Keep";
    return "Generated Doom Map";
  }

  private inferTheme(lower: string): string {
    if (["walmart", "warehouse", "store", "shelf", "shelves"].some((v) => lower.includes(v))) return "warehouse";
    if (["hell", "flesh", "gore", "lava"].some((v) => lower.includes(v))) return "hell";
    if (["stone", "ruin", "castle"].some((v) => lower.includes(v))) return "stone";
    if (["tech", "base", "computer"].some((v) => lower.includes(v))) return "techbase";
    return "techbase";
  }

  private inferDifficulty(lower: string): string {
    if (["easy", "light", "casual"].some((v) => lower.includes(v))) return "easy";
    if (["hard", "brutal", "slaughter"].some((v) => lower.includes(v))) return "hard";
    return "medium";
  }

  private inferRoomCount(lower: string, keys: string[]): number {
    const explicitMatch = lower.match(/\b([3-9]|1[0-2])\s+(rooms?|areas?)\b/);
    if (explicitMatch?.[1]) {
      const n = Number.parseInt(explicitMatch[1], 10);
      return Math.min(12, Math.max(3, n));
    }

    const base = 5 + keys.length;
    const wantsSmall = /\b(small|compact|tiny)\b/.test(lower);
    const wantsLarge = /\b(large|huge|big|massive)\b/.test(lower);
    if (wantsSmall) return Math.min(6, base);
    if (wantsLarge) return Math.min(12, base + 2);
    return Math.min(9, Math.max(5, base));
  }
}

export class TopologyAgent {
  name = "topology-agent";

  run(input: MapPromptProfile): MapPlan {
    const keys = input.requestedKeys;
    const nodes: RoomNode[] = [
      { id: "start", label: "Start Room", role: "start", required: true, notes: "Safe orientation space with one clear exit." },
      { id: "foyer", label: "Entry Foyer", role: "connector", required: true, notes: "Introduces the visual theme before combat pressure." },
      { id: "hub", label: "Main Arena", role: "arena", required: true, notes: "Primary combat and navigation landmark." },
    ];

    keys.forEach((key, index) => {
      nodes.push({
        id: `${key}_key`,
        label: `${capitalized(key)} Key Room`,
        role: "key_room",
        required: true,
        grantsKey: key,
        notes: `Optional-looking branch that grants the ${key} key before the matching lock.`,
      });
      nodes.push({
        id: `${key}_lock`,
        label: `${capitalized(key)} Locked Wing`,
        role: "locked_room",
        required: true,
        lock: key,
        notes: `Required progression room gated by the ${key} key.`,
      });
      if (index === 0) {
        nodes.push({
          id: "secret_cache",
          label: "Secret Cache",
          role: "secret",
          required: false,
          notes: "Small side reward that does not block map completion.",
        });
      }
    });

    nodes.push({ id: "exit", label: "Exit Room", role: "exit", required: true, notes: "Distinct final space with readable exit line." });

    const connections: ConnectionPlan[] = [
      { from: "start", to: "foyer", kind: "doorway", notes: "" },
      { from: "foyer", to: "hub", kind: "doorway", notes: "" },
    ];

    keys.forEach((key) => {
      connections.push({
        from: "hub",
        to: `${key}_key`,
        kind: "doorway",
        notes: `Reachable before the ${key} lock.`,
      });
      connections.push({
        from: "hub",
        to: `${key}_lock`,
        kind: "locked_door",
        requiredKey: key,
        notes: "",
      });
    });

    if (nodes.some((n) => n.id === "secret_cache")) {
      connections.push({
        from: "foyer",
        to: "secret_cache",
        kind: "secret_door",
        notes: "Optional early reward.",
      });
    }
    connections.push({ from: `${keys[keys.length - 1]}_lock`, to: "exit", kind: "doorway", notes: "" });

    return {
      profile: input,
      topology: nodes,
      connections,
      rooms: [],
      validation: defaultValidationReport(),
    };
  }
}

export class ConnectorDoorAgent {
  name = "connector-door-agent";

  run(input: MapPlan): MapPlan {
    const nodesById = new Map<string, RoomNode>(input.topology.map((n) => [n.id, n]));
    const normalized = new Map<string, ConnectionPlan>();

    const setConn = (conn: ConnectionPlan): void => {
      normalized.set(`${conn.from}->${conn.to}`, conn);
    };

    input.connections.forEach((connection) => {
      const target = nodesById.get(connection.to);
      const requiredKey = connection.requiredKey ?? target?.lock;

      const normalizedConnection: ConnectionPlan =
        connection.kind === "locked_door" && requiredKey
          ? {
              ...connection,
              requiredKey,
              notes: connection.notes.length > 0 ? connection.notes : `Locked by the ${requiredKey} key.`,
            }
          : connection;
      setConn(normalizedConnection);

      if (target?.grantsKey) {
        const loopback: ConnectionPlan = {
          from: connection.to,
          to: connection.from,
          kind: "loopback",
          notes: `Returns player to the main landmark after collecting the ${target.grantsKey} key.`,
        };
        const id = `${loopback.from}->${loopback.to}`;
        if (!normalized.has(id)) normalized.set(id, loopback);
      }

      if (connection.kind === "locked_door") {
        const loopback: ConnectionPlan = {
          from: connection.to,
          to: connection.from,
          kind: "loopback",
          notes: "Shortcut back after clearing the locked wing.",
        };
        const id = `${loopback.from}->${loopback.to}`;
        if (!normalized.has(id)) normalized.set(id, loopback);
      }
    });

    return {
      ...input,
      connections: Array.from(normalized.values()),
    };
  }
}

export class RoomLayoutAgent {
  name = "room-layout-agent";

  run(input: MapPlan): MapPlan {
    const textures = ThemeTextures.forTheme(input.profile.theme);
    const rooms: RoomPlan[] = input.topology.map((node, index) => {
      const [width, height] = this.sizeFor(
        node.role,
        input.profile.targetRoomCount,
        input.profile.requestedKeys.length,
        input.profile.requestedSetPieces,
      );
      return {
        id: node.id,
        shape: this.shapeFor(node.role, index),
        width,
        height,
        floorHeight: this.floorFor(node.role, index),
        ceilingHeight: this.ceilingFor(node.role),
        light: this.lightFor(node.role, input.profile.lightingHint),
        floorTexture: textures.floor,
        ceilingTexture: textures.ceiling,
        wallTexture: textures.wall,
        encounterHint: this.encounterFor(node.role, input.profile.difficulty),
        traversalHint: this.traversalFor(node, input.profile.requestedSetPieces),
        landmark: this.landmarkFor(node, input.profile.theme),
      };
    });

    return {
      ...input,
      rooms,
    };
  }

  private sizeFor(role: string, roomCount: number, keyCount: number, setPieces: string[]): [number, number] {
    switch (role) {
      case "start":
        return [256, 256];
      case "connector":
        return [384, 256];
      case "arena":
        if (setPieces.includes("aisle-arena") || keyCount >= 3) return [1536, 1024];
        if (roomCount >= 8) return [1024, 768];
        return [640, 512];
      case "key_room":
        return [384, 384];
      case "locked_room":
        return [512, 384];
      case "secret":
        return [224, 192];
      case "exit":
        return [320, 256];
      default:
        return [384, 320];
    }
  }

  private shapeFor(role: string, index: number): string {
    switch (role) {
      case "arena":
        return "cross";
      case "connector":
        return "hall";
      case "key_room":
        return index % 2 === 0 ? "l_shape" : "rectangle";
      case "locked_room":
        return "rectangle_with_alcoves";
      case "secret":
        return "closet";
      default:
        return "rectangle";
    }
  }

  private floorFor(role: string, index: number): number {
    switch (role) {
      case "arena":
        return 0;
      case "key_room":
        return index % 2 === 0 ? 8 : -8;
      case "locked_room":
        return 16;
      case "secret":
        return 0;
      default:
        return 0;
    }
  }

  private ceilingFor(role: string): number {
    switch (role) {
      case "arena":
        return 160;
      case "locked_room":
        return 144;
      default:
        return 128;
    }
  }

  private lightFor(role: string, hint: string): number {
    const base = hint === "dark" ? 112 : hint === "bright" ? 192 : 160;
    switch (role) {
      case "secret":
        return Math.max(80, base - 32);
      case "exit":
        return Math.min(240, base + 32);
      case "key_room":
        return Math.min(224, base + 16);
      default:
        return base;
    }
  }

  private encounterFor(role: string, difficulty: string): string {
    const pressure = difficulty === "easy" ? "light" : difficulty === "hard" ? "heavy" : "moderate";
    switch (role) {
      case "start":
        return "no monsters; player gets bearings";
      case "arena":
        return `${pressure} crossfire with cover and room to dodge`;
      case "key_room":
        return `${pressure} ambush after key pickup`;
      case "locked_room":
        return `${pressure} forward fight, not hitscan sniping`;
      case "secret":
        return "reward only";
      case "exit":
        return "small final guard group";
      default:
        return `${pressure} incidental patrol`;
    }
  }

  private traversalFor(node: RoomNode, setPieces: string[]): string {
    if (node.role === "arena" && setPieces.includes("aisle-arena")) {
      return "parallel shelf aisles with cross-cuts wide enough for monsters and player movement";
    }
    if (node.role === "locked_room" && setPieces.includes("bridge")) {
      return "simple bridge segment with an obvious fallback route";
    }
    if (node.role === "key_room" && setPieces.includes("hazard-floor")) {
      return "small lowered hazard pool with a clear escape lip";
    }
    if (node.role === "locked_room" && setPieces.includes("switch")) {
      return "switch opens a return shortcut after the fight";
    }
    return "flat passable floor with readable door approach";
  }

  private landmarkFor(node: RoomNode, theme: string): string {
    switch (node.role) {
      case "start":
        return `player-facing ${readable(theme)} sign`;
      case "arena":
        return "central landmark visible from each entrance";
      case "key_room":
        return `${node.grantsKey ?? "key"} pedestal in clear light`;
      case "locked_room":
        return `${node.lock ?? "key"} color trim around the lock`;
      case "secret":
        return "misaligned wall texture";
      case "exit":
        return "bright exit door and contrasting floor";
      default:
        return "texture break that marks progression";
    }
  }
}

export class MapPlanValidator {
  name = "map-plan-validator";

  run(input: MapPlan): MapPlan {
    const hardIssues: string[] = [];
    const softIssues: string[] = [];
    const roomIds = new Set(input.topology.map((r) => r.id));
    const roomsById = new Map<string, RoomNode>(input.topology.map((r) => [r.id, r]));
    const grantedKeys = new Set(input.topology.map((r) => r.grantsKey).filter((v): v is string => Boolean(v)));

    if (!input.topology.some((r) => r.role === "start")) hardIssues.push("Missing start room.");
    if (!input.topology.some((r) => r.role === "exit")) hardIssues.push("Missing exit room.");

    input.connections.forEach((connection) => {
      if (!roomIds.has(connection.from)) hardIssues.push(`Connection starts from unknown room ${connection.from}.`);
      if (!roomIds.has(connection.to)) hardIssues.push(`Connection ends at unknown room ${connection.to}.`);
      if (connection.requiredKey && !grantedKeys.has(connection.requiredKey)) {
        hardIssues.push(`Connection ${connection.from}->${connection.to} requires ${connection.requiredKey} key, but no room grants it.`);
      }
    });

    const reachable = this.reachableRooms(input, roomsById);
    input.topology.filter((r) => r.required).forEach((room) => {
      if (!reachable.has(room.id)) hardIssues.push(`Required room ${room.id} is not reachable with available keys.`);
    });
    if (input.topology.some((r) => r.role === "exit" && !reachable.has(r.id))) hardIssues.push("Exit is not reachable.");

    const layoutsById = new Map<string, RoomPlan>(input.rooms.map((r) => [r.id, r]));
    input.topology.forEach((node) => {
      if (!layoutsById.has(node.id)) hardIssues.push(`No room layout for ${node.id}.`);
    });

    input.rooms.forEach((room) => {
      if (room.width < 128 || room.height < 128) hardIssues.push(`Room ${room.id} is too small for Doom movement.`);
      if (room.ceilingHeight - room.floorHeight < 80) hardIssues.push(`Room ${room.id} has too little vertical clearance.`);
      if (room.light < 80 || room.light > 240) softIssues.push(`Room ${room.id} light ${room.light} is outside the preferred readable range.`);
    });

    if (input.connections.filter((c) => c.kind === "loopback").length === 0) softIssues.push("No loopback connection yet; map may feel linear.");
    if (input.topology.filter((r) => r.role === "secret").length === 0) softIssues.push("No optional secret yet.");

    const score = clamp(100 - hardIssues.length * 25 - softIssues.length * 6, 0, 100);
    const validation: ValidationReport = {
      passed: hardIssues.length === 0 && score >= 70,
      score,
      hardIssues,
      softIssues,
    };
    return { ...input, validation };
  }

  private reachableRooms(input: MapPlan, roomsById: Map<string, RoomNode>): Set<string> {
    const start = input.topology.find((r) => r.role === "start")?.id;
    if (!start) return new Set<string>();

    const reachable = new Set<string>([start]);
    const keys = new Set<string>();
    let changed: boolean;

    do {
      changed = false;

      Array.from(reachable).forEach((roomId) => {
        const granted = roomsById.get(roomId)?.grantsKey;
        if (granted && !keys.has(granted)) {
          keys.add(granted);
          changed = true;
        }
      });

      input.connections
        .filter((c) => reachable.has(c.from))
        .filter((c) => !c.requiredKey || keys.has(c.requiredKey))
        .forEach((c) => {
          if (!reachable.has(c.to)) {
            reachable.add(c.to);
            changed = true;
          }
        });
    } while (changed);

    return reachable;
  }
}

export class RootMapOrchestrator {
  private readonly promptProfileAgent: PromptProfileAgent;
  private readonly topologyAgent: TopologyAgent;
  private readonly connectorDoorAgent: ConnectorDoorAgent;
  private readonly roomLayoutAgent: RoomLayoutAgent;
  private readonly validator: MapPlanValidator;

  constructor(
    promptProfileAgent: PromptProfileAgent = new PromptProfileAgent(),
    topologyAgent: TopologyAgent = new TopologyAgent(),
    connectorDoorAgent: ConnectorDoorAgent = new ConnectorDoorAgent(),
    roomLayoutAgent: RoomLayoutAgent = new RoomLayoutAgent(),
    validator: MapPlanValidator = new MapPlanValidator(),
  ) {
    this.promptProfileAgent = promptProfileAgent;
    this.topologyAgent = topologyAgent;
    this.connectorDoorAgent = connectorDoorAgent;
    this.roomLayoutAgent = roomLayoutAgent;
    this.validator = validator;
  }

  generate(prompt: string): MapPlan {
    const profile = this.promptProfileAgent.run(prompt);
    const topologyPlan = this.topologyAgent.run(profile);
    const connectorPlan = this.connectorDoorAgent.run(topologyPlan);
    const roomPlan = this.roomLayoutAgent.run(connectorPlan);
    return this.validator.run(roomPlan);
  }
}

function capitalized(value: string): string {
  if (value.length === 0) return value;
  return value[0].toUpperCase() + value.slice(1);
}

function readable(value: string): string {
  return value.replaceAll("_", " ");
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
