import { existsSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { WadParser } from "../wad/wadParser.js";
import { WadWriter } from "../wad/wadWriter.js";
import { generateSvgFromLevel } from "../wad/svgGenerator.js";

function main(): void {
  console.log("WAD Tool - DOOM/DOOM2 WAD File Parser and Writer");
  console.log("================================================");

  const wadFilePath = resolve(process.cwd(), "..", "WADTool", "data", "ATTACK.WAD");
  const generatedWadPath = resolve(process.cwd(), "..", "WADTool", "data", "ATTACK_GEN.WAD");
  const svgPath = resolve(process.cwd(), "..", "WADTool", "data", "level.svg");

  if (!existsSync(wadFilePath)) {
    console.log(`Error: WAD file not found at ${wadFilePath}`);
    return;
  }

  const parser = new WadParser(wadFilePath);
  const success = parser.parse();
  if (!success) {
    console.log("Failed to parse WAD file.");
    return;
  }

  console.log("\nParsing completed successfully!");
  console.log(parser.getSummary());

  const svgResult = generateSvgFromLevel(
    {
      vertexes: parser.vertexes,
      lineDefs: parser.lineDefs,
      sideDefs: parser.sideDefs,
      sectors: parser.sectors,
      things: parser.things,
      nodes: parser.nodes,
      subSectors: parser.subSectors,
      segs: parser.segs,
      annotations: [],
    },
    svgPath,
  );
  if (!svgResult.ok) {
    console.log("Failed to generate SVG file.");
  } else {
    console.log(`SVG generated: ${svgPath}`);
  }

  const writer = new WadWriter(generatedWadPath);
  const wrote = writer.write(
    parser.vertexes,
    parser.lineDefs,
    parser.sideDefs,
    parser.sectors,
    parser.things,
    parser.nodes,
    parser.subSectors,
    parser.segs,
  );
  if (!wrote) {
    console.log("Failed to generate WAD file.");
    return;
  }
  console.log(`WAD generated: ${generatedWadPath}`);

  const verify = new WadParser(generatedWadPath);
  if (!verify.parse()) {
    console.log("Failed to parse generated WAD file.");
    return;
  }

  const mismatches = compareWadSizes(parser, verify);
  if (mismatches.length === 0) {
    console.log("All data structures match in size between original and generated WAD files!");
  } else {
    mismatches.forEach((msg) => console.log(msg));
  }

  writeFileSync(resolve(process.cwd(), "..", "WADTool", "data", "ATTACK_GEN_SUMMARY.txt"), verify.getSummary(), "utf-8");
}

function compareWadSizes(original: WadParser, generated: WadParser): string[] {
  const out: string[] = [];
  if (original.vertexes.length !== generated.vertexes.length) out.push(`Vertex count mismatch: Original=${original.vertexes.length}, Generated=${generated.vertexes.length}`);
  if (original.lineDefs.length !== generated.lineDefs.length) out.push(`LineDef count mismatch: Original=${original.lineDefs.length}, Generated=${generated.lineDefs.length}`);
  if (original.sideDefs.length !== generated.sideDefs.length) out.push(`SideDef count mismatch: Original=${original.sideDefs.length}, Generated=${generated.sideDefs.length}`);
  if (original.sectors.length !== generated.sectors.length) out.push(`Sector count mismatch: Original=${original.sectors.length}, Generated=${generated.sectors.length}`);
  if (original.things.length !== generated.things.length) out.push(`Thing count mismatch: Original=${original.things.length}, Generated=${generated.things.length}`);
  if (original.nodes.length !== generated.nodes.length) out.push(`Node count mismatch: Original=${original.nodes.length}, Generated=${generated.nodes.length}`);
  if (original.subSectors.length !== generated.subSectors.length) out.push(`SubSector count mismatch: Original=${original.subSectors.length}, Generated=${generated.subSectors.length}`);
  if (original.segs.length !== generated.segs.length) out.push(`Seg count mismatch: Original=${original.segs.length}, Generated=${generated.segs.length}`);
  return out;
}

main();
