import { app, BrowserWindow, ipcMain } from "electron";
import { readFileSync, writeFileSync, readdirSync, statSync } from "node:fs";
import { resolve, join } from "node:path";
import { spawn } from "node:child_process";

const repoRoot = resolve(process.cwd(), "..");
const tsRoot = process.cwd();
const promptFilePath = resolve(repoRoot, "prompt.txt");
const promptRuntimePath = resolve(repoRoot, "WADTool", "data", "prompt.electron.txt");
const mapPlanPath = resolve(repoRoot, "WADTool", "data", "map_plan.ts.json");
const wadPath = resolve(repoRoot, "WADTool", "data", "AGENT_TS.WAD");
const compiledPath = resolve(repoRoot, "WADTool", "data", "compiled_level.ts.json");
const svgPath = resolve(repoRoot, "WADTool", "data", "AGENT_TS.svg");
const levelsRoot = resolve(repoRoot, "WADTool", "data", "levels");
const baseUrl = "http://127.0.0.1:8080";
const gradleRunnerPath = resolve(repoRoot, "gradle", "wrapper", "gradle-8.14", "bin", "gradle.bat");
const localJavaHome = resolve(repoRoot, ".tools", "corretto-24", "jdk24.0.2_12");

let mainWindow = null;
let serverProcessPid = null;

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1600,
    height: 1000,
    webPreferences: {
      preload: resolve(tsRoot, "electron", "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  mainWindow.loadFile(resolve(tsRoot, "electron", "renderer", "index.html"));
}

app.whenReady().then(() => {
  app.on("second-instance", () => {
    if (!mainWindow) return;
    if (mainWindow.isMinimized()) mainWindow.restore();
    mainWindow.focus();
  });

  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});

ipcMain.handle("app:getInitialPrompt", () => {
  try {
    return readFileSync(promptFilePath, "utf-8");
  } catch {
    return "";
  }
});

ipcMain.handle("app:getServerStatus", async () => {
  try {
    const res = await fetch(baseUrl);
    return { ok: res.ok, status: res.status };
  } catch {
    return { ok: false, status: 0 };
  }
});

ipcMain.handle("app:startServer", async () => {
  const status = await checkServerStatus();
  if (status.ok) {
    return { ok: true, alreadyRunning: true, pid: null };
  }

  const env = { ...process.env };
  if (!env.JAVA_HOME) {
    env.JAVA_HOME = localJavaHome;
    env.Path = `${join(localJavaHome, "bin")};${env.Path ?? ""}`;
  }

  const child = spawn(gradleRunnerPath, ["--no-daemon", "run"], {
    cwd: repoRoot,
    detached: true,
    windowsHide: true,
    stdio: "ignore",
    env,
  });
  child.unref();
  serverProcessPid = child.pid ?? null;

  for (let i = 0; i < 20; i += 1) {
    await sleep(500);
    const now = await checkServerStatus();
    if (now.ok) {
      return { ok: true, alreadyRunning: false, pid: serverProcessPid };
    }
  }

  return { ok: false, error: "Server did not become ready on port 8080 within timeout." };
});

ipcMain.handle("app:listLevels", () => {
  try {
    const dirs = readdirSync(levelsRoot, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => {
        const id = entry.name;
        const dirPath = join(levelsRoot, id);
        const createdPath = join(dirPath, "created.txt");
        const promptPath = join(dirPath, "prompt.txt");
        let createdAt = "";
        let prompt = "";
        try {
          createdAt = readFileSync(createdPath, "utf-8").trim();
        } catch {
          const mtime = statSync(dirPath).mtime;
          createdAt = mtime.toISOString();
        }
        try {
          prompt = readFileSync(promptPath, "utf-8").trim();
        } catch {
          prompt = "";
        }
        return { id, createdAt, prompt };
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return { ok: true, levels: dirs.slice(0, 60) };
  } catch (error) {
    return { ok: false, error: String(error), levels: [] };
  }
});

ipcMain.handle("app:generateLevel", async (_event, prompt) => {
  const text = typeof prompt === "string" ? prompt.trim() : "";
  if (text.length === 0) {
    return { ok: false, error: "Prompt is empty." };
  }

  writeFileSync(promptRuntimePath, `${text}\n`, "utf-8");

  const tscPath = resolve(tsRoot, "node_modules", "typescript", "lib", "tsc.js");
  const buildResult = await runProcess(process.execPath, [tscPath, "-p", "tsconfig.json"], { cwd: tsRoot });
  if (buildResult.code !== 0) {
    return {
      ok: false,
      error: buildResult.stderr || buildResult.stdout || "TypeScript build failed.",
    };
  }

  const scriptPath = resolve(tsRoot, "dist", "agent", "generateWad.js");
  const result = await runProcess(
    process.execPath,
    [scriptPath, promptRuntimePath, mapPlanPath, wadPath, compiledPath, svgPath],
    { cwd: tsRoot },
  );
  if (result.code !== 0) {
    return { ok: false, error: result.stderr || result.stdout || "Generation failed." };
  }

  const match = result.stdout.match(/Saved level to storage with id:\s*(\S+)/);
  const levelId = match?.[1] ?? null;
  if (!levelId) {
    return { ok: false, error: "Generation completed but level id was not found in output." };
  }

  return {
    ok: true,
    levelId,
    playUrl: `${baseUrl}/levels/${levelId}/play`,
    output: result.stdout,
  };
});

async function checkServerStatus() {
  try {
    const res = await fetch(baseUrl);
    return { ok: res.ok, status: res.status };
  } catch {
    return { ok: false, status: 0 };
  }
}

function sleep(ms) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, ms));
}

function runProcess(command, args, opts = {}) {
  return new Promise((resolvePromise) => {
    const env = sanitizeEnvForWindows(opts.env ?? process.env);
    const child = spawn(command, args, {
      ...opts,
      env,
      shell: false,
      windowsHide: true,
    });

    let stdout = "";
    let stderr = "";
    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });
    child.on("close", (code) => {
      resolvePromise({ code: code ?? 1, stdout, stderr });
    });
    child.on("error", (error) => {
      resolvePromise({ code: 1, stdout, stderr: `${stderr}\n${String(error)}`.trim() });
    });
  });
}

function sanitizeEnvForWindows(rawEnv) {
  if (process.platform !== "win32") return { ...rawEnv };

  const env = {};
  const pathKeys = Object.keys(rawEnv).filter((key) => key.toLowerCase() === "path");
  const canonicalPathKey = pathKeys.find((key) => key === "Path") ?? pathKeys[0] ?? "Path";
  const canonicalPathValue = rawEnv[canonicalPathKey];

  Object.entries(rawEnv).forEach(([key, value]) => {
    if (key.toLowerCase() === "path") return;
    env[key] = value;
  });
  env.Path = canonicalPathValue ?? "";
  return env;
}
