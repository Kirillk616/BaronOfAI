import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { randomUUID } from "node:crypto";

export interface LevelMeta {
  id: string;
  prompt: string;
  createdAt: Date;
}

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

  getWad(id: string): string | null {
    const filePath = join(this.root, id, "map.wad");
    return existsSync(filePath) ? filePath : null;
  }

  getPrompt(id: string): string | null {
    try {
      return readFileSync(join(this.root, id, "prompt.txt"), "utf-8");
    } catch {
      return null;
    }
  }

  list(limit: number = 50): LevelMeta[] {
    if (!existsSync(this.root)) return [];
    const dirs = readdirSync(this.root, { withFileTypes: true }).filter((entry) => entry.isDirectory());
    const items: LevelMeta[] = [];
    dirs.forEach((entry) => {
      const id = entry.name;
      const dir = join(this.root, id);
      const prompt = this.getPrompt(id);
      if (prompt == null) return;
      const createdPath = join(dir, "created.txt");
      let createdAt: Date;
      try {
        createdAt = new Date(readFileSync(createdPath, "utf-8").trim());
        if (Number.isNaN(createdAt.getTime())) throw new Error("invalid date");
      } catch {
        createdAt = statSync(dir).mtime;
      }
      items.push({ id, prompt, createdAt });
    });
    items.sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
    return items.slice(0, limit);
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
