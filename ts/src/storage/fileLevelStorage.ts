import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { randomUUID } from "node:crypto";

export class FileLevelStorage {
  private readonly root: string;

  constructor(root: string = "WADTool/data/levels") {
    this.root = resolve(process.cwd(), root);
    mkdirSync(this.root, { recursive: true });
  }

  save(prompt: string, wadPath: string, svgContent: string): string {
    const id = this.generateId();
    const dir = join(this.root, id);
    mkdirSync(dir, { recursive: true });

    writeFileSync(join(dir, "prompt.txt"), prompt, "utf-8");
    writeFileSync(join(dir, "map.svg"), svgContent, "utf-8");
    copyFileSync(resolve(wadPath), join(dir, "map.wad"));
    writeFileSync(join(dir, "created.txt"), new Date().toISOString(), "utf-8");
    return id;
  }

  getSvg(id: string): string | null {
    try {
      return readFileSync(join(this.root, id, "map.svg"), "utf-8");
    } catch {
      return null;
    }
  }

  private generateId(): string {
    const now = new Date();
    const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
    return `${stamp}_${randomUUID().slice(0, 8)}`;
  }
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}
