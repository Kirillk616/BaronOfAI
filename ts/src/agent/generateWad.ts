import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { RootMapOrchestrator } from "./mapAgents.js";
import { MapPlanCompiler } from "./mapPlanCompiler.js";
import { WadWriter } from "../wad/wadWriter.js";
import { generateSvgFromLevel } from "../wad/svgGenerator.js";
import { FileLevelStorage } from "../storage/fileLevelStorage.js";

function main(): void {
  const promptPath = process.argv[2] ?? "../prompt.txt";
  const mapPlanOutputPath = process.argv[3] ?? "../WADTool/data/map_plan.ts.json";
  const wadOutputPath = process.argv[4] ?? "../WADTool/data/AGENT_TS.WAD";
  const compiledLevelOutputPath = process.argv[5] ?? "../WADTool/data/compiled_level.ts.json";
  const svgOutputPath = process.argv[6] ?? "../WADTool/data/AGENT_TS.svg";

  const resolvedPromptPath = resolve(process.cwd(), promptPath);
  const resolvedMapPlanOutputPath = resolve(process.cwd(), mapPlanOutputPath);
  const resolvedWadOutputPath = resolve(process.cwd(), wadOutputPath);
  const resolvedCompiledLevelOutputPath = resolve(process.cwd(), compiledLevelOutputPath);
  const resolvedSvgOutputPath = resolve(process.cwd(), svgOutputPath);

  const prompt = readFileSync(resolvedPromptPath, "utf-8").trim();
  if (prompt.length === 0) {
    throw new Error(`Prompt file is empty: ${resolvedPromptPath}`);
  }

  const plan = new RootMapOrchestrator().generate(prompt);
  const level = new MapPlanCompiler().compile(plan);
  const ok = new WadWriter(resolvedWadOutputPath).writeLevel(level);
  if (!ok) {
    throw new Error(`Failed to write WAD: ${resolvedWadOutputPath}`);
  }
  const svgResult = generateSvgFromLevel(level, resolvedSvgOutputPath);
  if (!svgResult.ok) {
    throw new Error("Failed to generate SVG from compiled level.");
  }

  mkdirSync(dirname(resolvedMapPlanOutputPath), { recursive: true });
  writeFileSync(resolvedMapPlanOutputPath, `${JSON.stringify(plan, null, 2)}\n`, "utf-8");
  mkdirSync(dirname(resolvedCompiledLevelOutputPath), { recursive: true });
  writeFileSync(resolvedCompiledLevelOutputPath, `${JSON.stringify(level, null, 2)}\n`, "utf-8");
  const levelId = new FileLevelStorage("../WADTool/data/levels").save(prompt, resolvedWadOutputPath, svgResult.svg);

  console.log(`Map plan written to: ${resolvedMapPlanOutputPath}`);
  console.log(`Compiled level JSON written to: ${resolvedCompiledLevelOutputPath}`);
  console.log(`SVG written to: ${resolvedSvgOutputPath}`);
  console.log(`WAD written to: ${resolvedWadOutputPath}`);
  console.log(`Saved level to storage with id: ${levelId}`);
  console.log(`Browser play URL: /levels/${levelId}/play`);
  console.log(`Validation: ${plan.validation.passed ? "passed" : "failed"} (${plan.validation.score})`);
  console.log(`Geometry: vertexes=${level.vertexes.length}, linedefs=${level.lineDefs.length}, sectors=${level.sectors.length}`);
}

main();
