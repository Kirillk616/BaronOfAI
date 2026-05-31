import { writeFileSync } from "node:fs";
import type { BuiltLevel } from "./levelBuilder.js";

export function generateSvgFromLevel(level: BuiltLevel, outputPath?: string): { ok: boolean; svg: string } {
  if (level.vertexes.length === 0 || level.lineDefs.length === 0) {
    return { ok: false, svg: "" };
  }

  let minX = Number.POSITIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;

  level.vertexes.forEach((vertex) => {
    minX = Math.min(minX, vertex.x);
    minY = Math.min(minY, -vertex.y);
    maxX = Math.max(maxX, vertex.x);
    maxY = Math.max(maxY, -vertex.y);
  });

  const padding = 20;
  minX -= padding;
  minY -= padding;
  maxX += padding;
  maxY += padding;

  const width = Math.max(1, maxX - minX);
  const height = Math.max(1, maxY - minY);
  const svgWidth = 1200;
  const svgHeight = 900;
  const scaleX = svgWidth / width;
  const scaleY = svgHeight / height;
  const scale = Math.min(scaleX, scaleY);

  const lines: string[] = [];
  lines.push('<?xml version="1.0" encoding="UTF-8" standalone="no"?>');
  lines.push(`<svg width="${svgWidth}" height="${svgHeight}" xmlns="http://www.w3.org/2000/svg">`);
  lines.push('  <rect width="100%" height="100%" fill="black"/>');
  lines.push(`  <g transform="translate(${svgWidth / 2}, ${svgHeight / 2}) scale(${scale}) translate(${-(minX + maxX) / 2}, ${-(minY + maxY) / 2})">`);

  level.lineDefs.forEach((lineDef) => {
    const start = level.vertexes[lineDef.startVertex];
    const end = level.vertexes[lineDef.endVertex];
    if (!start || !end) return;
    lines.push(
      `    <line x1="${start.x}" y1="${-start.y}" x2="${end.x}" y2="${-end.y}" stroke="#00FF00" stroke-width="${2 / scale}"/>`,
    );
  });

  level.things.forEach((thing) => {
    if (thing.type !== 1) return;
    const lineX = thing.x + 20 * Math.cos((thing.angle * Math.PI) / 180);
    const lineY = -thing.y - 20 * Math.sin((thing.angle * Math.PI) / 180);
    lines.push(`    <circle cx="${thing.x}" cy="${-thing.y}" r="${8 / scale}" fill="blue"/>`);
    lines.push(
      `    <line x1="${thing.x}" y1="${-thing.y}" x2="${lineX}" y2="${lineY}" stroke="blue" stroke-width="${2 / scale}"/>`,
    );
  });

  level.annotations.forEach((annotation) => {
    const title = annotation.title.length > 0 ? annotation.title : `Room ${annotation.text}`;
    lines.push("    <g>");
    lines.push(`      <title>${xmlEscape(title)}</title>`);
    lines.push(
      `      <circle cx="${annotation.x}" cy="${-annotation.y}" r="${22 / scale}" fill="#101820" stroke="#FFD54A" stroke-width="${3 / scale}"/>`,
    );
    lines.push(
      `      <text x="${annotation.x}" y="${-annotation.y}" fill="#FFD54A" font-family="Arial, sans-serif" font-size="${24 / scale}" font-weight="700" text-anchor="middle" dominant-baseline="central">${xmlEscape(annotation.text)}</text>`,
    );
    lines.push("    </g>");
  });

  lines.push("  </g>");
  lines.push("</svg>");
  const svg = `${lines.join("\n")}\n`;

  if (outputPath) {
    writeFileSync(outputPath, svg, "utf-8");
  }

  return { ok: true, svg };
}

function xmlEscape(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}
