import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { WadReader } from "../wad/wadReader.js";

function main(): void {
  const wadArg = process.argv[2];
  const outputArg = process.argv[3];
  const outputDir = outputArg ? resolve(process.cwd(), outputArg) : resolve(process.cwd(), "..", "WADTool", "data");

  const defaultCandidates = [
    resolve(process.cwd(), "..", "WADTool", "data", "ATTACK.WAD"),
    resolve(process.cwd(), "..", "WADTool", "data", "DOOM2.WAD"),
  ];

  const wadPath = wadArg ? resolve(process.cwd(), wadArg) : defaultCandidates.find((candidate) => existsSync(candidate));
  if (!wadPath || !existsSync(wadPath)) {
    console.log("WadReader test: file not found.");
    console.log("Usage: wad:read <path-to.wad> [output-dir]");
    return;
  }

  console.log(`WadReader test: exporting levels from ${wadPath} to ${outputDir}`);
  const ok = new WadReader(wadPath).exportLevelsAsJson(outputDir);
  if (!ok) {
    console.log("WadReader export failed");
  }
}

main();
