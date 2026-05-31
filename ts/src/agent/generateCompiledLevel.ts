import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { RootMapOrchestrator } from "./mapAgents.js";
import { MapPlanCompiler } from "./mapPlanCompiler.js";

function main(): void {
  const promptPath = process.argv[2] ?? "../prompt.txt";
  const outputPath = process.argv[3] ?? "../WADTool/data/compiled_level.ts.json";

  const resolvedPromptPath = resolve(process.cwd(), promptPath);
  const resolvedOutputPath = resolve(process.cwd(), outputPath);
  const prompt = readFileSync(resolvedPromptPath, "utf-8").trim();

  if (prompt.length === 0) {
    throw new Error(`Prompt file is empty: ${resolvedPromptPath}`);
  }

  const plan = new RootMapOrchestrator().generate(prompt);
  const level = new MapPlanCompiler().compile(plan);

  mkdirSync(dirname(resolvedOutputPath), { recursive: true });
  writeFileSync(resolvedOutputPath, `${JSON.stringify(level, null, 2)}\n`, "utf-8");

  console.log(`Compiled level JSON written to: ${resolvedOutputPath}`);
  console.log(`Vertexes: ${level.vertexes.length}`);
  console.log(`Linedefs: ${level.lineDefs.length}`);
  console.log(`Sidedefs: ${level.sideDefs.length}`);
  console.log(`Sectors: ${level.sectors.length}`);
  console.log(`Things: ${level.things.length}`);
  console.log(`Nodes: ${level.nodes.length}`);
  console.log(`Subsectors: ${level.subSectors.length}`);
  console.log(`Segs: ${level.segs.length}`);
  console.log(`Annotations: ${level.annotations.length}`);
}

main();
