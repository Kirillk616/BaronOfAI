import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { RootMapOrchestrator } from "./mapAgents.js";

function main(): void {
  const promptPath = process.argv[2] ?? "../prompt.txt";
  const outputPath = process.argv[3] ?? "../WADTool/data/map_plan.ts.json";

  const resolvedPromptPath = resolve(process.cwd(), promptPath);
  const resolvedOutputPath = resolve(process.cwd(), outputPath);
  const prompt = readFileSync(resolvedPromptPath, "utf-8").trim();

  if (prompt.length === 0) {
    throw new Error(`Prompt file is empty: ${resolvedPromptPath}`);
  }

  const plan = new RootMapOrchestrator().generate(prompt);
  mkdirSync(dirname(resolvedOutputPath), { recursive: true });
  writeFileSync(resolvedOutputPath, `${JSON.stringify(plan, null, 2)}\n`, "utf-8");

  console.log(`Map plan written to: ${resolvedOutputPath}`);
  console.log(`Title: ${plan.profile.title}`);
  console.log(`Theme: ${plan.profile.theme}, difficulty: ${plan.profile.difficulty}`);
  console.log(`Rooms: ${plan.topology.length}, connections: ${plan.connections.length}`);
  console.log(`Validation: ${plan.validation.passed ? "passed" : "failed"} (${plan.validation.score})`);
}

main();
