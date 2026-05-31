import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { buildLevelFromSpec, type LevelSpec } from "../wad/levelBuilder.js";
import { WadWriter } from "../wad/wadWriter.js";
import { WadParser } from "../wad/wadParser.js";
import { generateSvgFromLevel } from "../wad/svgGenerator.js";

function main(): void {
  const promptArg = process.argv[2];
  const outputArg = process.argv[3];
  const promptPath = promptArg ? resolve(process.cwd(), promptArg) : resolve(process.cwd(), "..", "prompt.txt");
  const outputPath = outputArg ? resolve(process.cwd(), outputArg) : resolve(process.cwd(), "..", "WADTool", "data", "GENAI.WAD");

  if (!existsSync(promptPath)) {
    if (promptArg == null) {
      console.log("Error: Default prompt file 'prompt.txt' not found in current directory.");
      console.log("Usage: prompt:generate <prompt.txt> [output.wad]");
    } else {
      console.log(`Error: Prompt file not found at ${promptPath}`);
    }
    return;
  }

  const prompt = readFileSync(promptPath, "utf-8").trim();
  if (prompt.length === 0) {
    console.log("Error: Prompt file is empty");
    return;
  }

  console.log(`Reading prompt from: ${promptPath}`);
  console.log("Processing prompt with Koog...");

  processPrompt(prompt)
    .then((spec) => {
      console.log(`Prompt processed into level spec: ${JSON.stringify(spec)}`);
      const level = buildLevelFromSpec(spec);
      const writer = new WadWriter(outputPath);
      const ok = writer.writeLevel(level);
      if (!ok) {
        console.log(`Failed to generate WAD at: ${outputPath}`);
        return;
      }
      console.log(`Generated WAD at: ${outputPath}`);
      const parser = new WadParser(outputPath);
      if (!parser.parse()) {
        console.log("Warning: Could not parse generated WAD to produce SVG.");
        return;
      }
      const svgPath = outputPath.replace(/\.wad$/i, ".svg");
      const svg = generateSvgFromLevel(level, svgPath);
      if (!svg.ok) {
        console.log("Warning: SVG generation failed.");
      }
    })
    .catch((error) => {
      console.log(`Warning: prompt processing failed: ${String(error)}. Falling back to heuristic.`);
      const spec = heuristic(prompt);
      const level = buildLevelFromSpec(spec);
      const writer = new WadWriter(outputPath);
      if (!writer.writeLevel(level)) {
        console.log(`Failed to generate WAD at: ${outputPath}`);
      } else {
        console.log(`Generated WAD at: ${outputPath}`);
      }
    });
}

async function processPrompt(prompt: string): Promise<LevelSpec> {
  const apiKey = process.env.OPENAI_API_KEY?.trim();
  const model = process.env.KOOG_MODEL?.trim() || "gpt-4o-mini";
  if (!apiKey) return heuristic(prompt);
  try {
    const aiSpec = await requestOpenAiSpec(prompt, apiKey, model);
    return sanitizeSpec(aiSpec);
  } catch (error) {
    console.log(`Warning: Koog/OpenAI call failed: ${String(error)}. Falling back to heuristic.`);
    return heuristic(prompt);
  }
}

interface AiSpec {
  roomSize?: number;
  floorHeight?: number;
  ceilingHeight?: number;
  floorTex?: string;
  ceilTex?: string;
  wallTex?: string;
  light?: number;
  playerAngle?: number;
}

async function requestOpenAiSpec(prompt: string, apiKey: string, model: string): Promise<AiSpec> {
  const system = [
    "You are a level spec generator for a DOOM-like map.",
    "Output strict JSON only with keys: roomSize,floorHeight,ceilingHeight,floorTex,ceilTex,wallTex,light,playerAngle.",
    "No markdown and no prose.",
  ].join(" ");
  const body = {
    model,
    temperature: 0.2,
    messages: [
      { role: "system", content: system },
      { role: "user", content: `Create a level spec from this prompt: "${prompt}". Return JSON only.` },
    ],
  };
  const response = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`OpenAI HTTP error ${response.status}: ${await response.text()}`);
  }
  const json = (await response.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const content = json.choices?.[0]?.message?.content?.trim();
  if (!content) throw new Error("OpenAI response missing content");
  return JSON.parse(content) as AiSpec;
}

function sanitizeSpec(input: AiSpec): LevelSpec {
  const roomSize = clampInt(input.roomSize ?? 384, 128, 4096);
  const floorHeight = clampInt(input.floorHeight ?? 0, -4096, 4096);
  const rawCeiling = clampInt(input.ceilingHeight ?? 128, 0, 8192);
  const ceilingHeight = rawCeiling <= floorHeight ? floorHeight + 64 : rawCeiling;
  const light = clampInt(input.light ?? 160, 0, 255);
  const angle = modulo360(input.playerAngle ?? 0);
  return {
    roomSize,
    floorHeight,
    ceilingHeight,
    floorTex: nonEmpty(input.floorTex, "FLOOR0_1"),
    ceilTex: nonEmpty(input.ceilTex, "CEIL1_1"),
    wallTex: nonEmpty(input.wallTex, "STARTAN2"),
    light,
    playerAngle: angle,
  };
}

function heuristic(prompt: string): LevelSpec {
  const lower = prompt.toLowerCase();
  const sizeMatch = lower.match(/(?:^|\s)([1-9][0-9]{1,3})(?:\s|$)/);
  const roomSize = clampInt(sizeMatch ? Number.parseInt(sizeMatch[1], 10) : 384, 128, 4096);
  const light =
    hasAny(lower, ["dark", "dim", "gloom"]) ? 96
      : hasAny(lower, ["bright", "shiny", "sunny"]) ? 224
        : 160;
  const wallTex =
    hasAny(lower, ["tech", "base", "computer"]) ? "STARTAN2"
      : hasAny(lower, ["hell", "flesh", "gore"]) ? "SKINFACE"
        : hasAny(lower, ["stone", "ruin", "castle"]) ? "STONE2"
          : "STARTAN2";
  const floorTex =
    hasAny(lower, ["lava", "fire"]) ? "LAVA1"
      : hasAny(lower, ["water", "pool"]) ? "FWATER1"
        : "FLOOR0_1";
  const ceilTex = hasAny(lower, ["sky", "outdoor"]) ? "F_SKY1" : "CEIL1_1";
  const playerAngle =
    lower.includes("north") ? 0
      : lower.includes("east") ? 90
        : lower.includes("south") ? 180
          : lower.includes("west") ? 270
            : 0;
  return { roomSize, floorHeight: 0, ceilingHeight: 128, floorTex, ceilTex, wallTex, light, playerAngle };
}

function hasAny(text: string, needles: string[]): boolean {
  return needles.some((needle) => text.includes(needle));
}

function clampInt(value: number, min: number, max: number): number {
  const rounded = Math.trunc(value);
  return Math.min(max, Math.max(min, rounded));
}

function modulo360(value: number): number {
  const raw = Math.trunc(value) % 360;
  return raw < 0 ? raw + 360 : raw;
}

function nonEmpty(value: string | undefined, fallback: string): string {
  const text = value?.trim();
  return text && text.length > 0 ? text : fallback;
}

main();
