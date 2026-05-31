const promptEl = document.getElementById("prompt");
const generateBtn = document.getElementById("generateBtn");
const startServerBtn = document.getElementById("startServerBtn");
const refreshBtn = document.getElementById("refreshBtn");
const serverStatusEl = document.getElementById("serverStatus");
const levelStatusEl = document.getElementById("levelStatus");
const levelsListEl = document.getElementById("levelsList");
const logEl = document.getElementById("log");
const frameEl = document.getElementById("playerFrame");
const svgFrameEl = document.getElementById("svgFrame");

let latestPlayUrl = "http://127.0.0.1:8080";
let latestSvgUrl = "http://127.0.0.1:8080";
let latestLevelId = null;

function api() {
  if (!window.baronApp) {
    throw new Error("Desktop bridge unavailable. Restart Electron to reload preload.");
  }
  return window.baronApp;
}

function requireApiMethod(name) {
  const bridge = api();
  const fn = bridge?.[name];
  if (typeof fn !== "function") {
    throw new Error(`Desktop bridge method missing: ${name}. Close old Electron windows and start the latest app.`);
  }
  return fn.bind(bridge);
}

function setLog(text) {
  logEl.textContent = text;
  logEl.scrollTop = logEl.scrollHeight;
}

async function loadInitial() {
  try {
    const prompt = await requireApiMethod("getInitialPrompt")();
    promptEl.value = prompt;
  } catch (error) {
    setLog(`Failed to load prompt: ${String(error)}`);
  }
  await refreshServerStatus();
  await refreshLevels();
}

async function refreshServerStatus() {
  const status = await requireApiMethod("getServerStatus")();
  if (status.ok) {
    serverStatusEl.textContent = `Online (${status.status})`;
    serverStatusEl.style.color = "#6de1a8";
  } else {
    serverStatusEl.textContent = "Offline";
    serverStatusEl.style.color = "#f18383";
  }
}

async function refreshLevels() {
  const result = await requireApiMethod("listLevels")();
  if (!result.ok) {
    levelsListEl.innerHTML = `<div class="level-item muted">Failed to load levels.</div>`;
    return;
  }

  if (!result.levels.length) {
    levelsListEl.innerHTML = `<div class="level-item muted">No generated levels yet.</div>`;
    return;
  }

  levelsListEl.innerHTML = "";
  result.levels.forEach((level) => {
    const button = document.createElement("button");
    button.className = `level-item${latestLevelId === level.id ? " active" : ""}`;
    const prompt = level.prompt.length > 80 ? `${level.prompt.slice(0, 80)}...` : level.prompt;
    button.innerHTML = `<div class="id">${level.id}</div><div class="prompt">${escapeHtml(prompt || "(empty prompt)")}</div>`;
    button.addEventListener("click", () => {
      selectLevel(level.id);
    });
    levelsListEl.appendChild(button);
  });
}

function selectLevel(levelId) {
  latestLevelId = levelId;
  latestPlayUrl = `http://127.0.0.1:8080/levels/${levelId}/play`;
  latestSvgUrl = `http://127.0.0.1:8080/levels/${levelId}/svg`;
  frameEl.src = latestPlayUrl;
  svgFrameEl.src = latestSvgUrl;
  levelStatusEl.textContent = levelId;
  void refreshLevels();
}

async function generateAndPlay() {
  generateBtn.disabled = true;
  setLog("Running TypeScript pipeline...");
  try {
    const result = await requireApiMethod("generateLevel")(promptEl.value);
    if (!result.ok) {
      setLog(result.error || "Generation failed.");
      return;
    }
    selectLevel(result.levelId);
    setLog(result.output || `Generated level ${result.levelId}`);
    await refreshServerStatus();
    await refreshLevels();
  } catch (error) {
    setLog(`Generation error: ${String(error)}`);
  } finally {
    generateBtn.disabled = false;
  }
}

async function startServer() {
  startServerBtn.disabled = true;
  setLog("Starting backend server...");
  try {
    const result = await requireApiMethod("startServer")();
    if (!result.ok) {
      setLog(result.error || "Failed to start server.");
      return;
    }
    if (result.alreadyRunning) {
      setLog("Server is already running.");
    } else {
      setLog(`Server started (pid: ${result.pid ?? "unknown"}).`);
    }
    await refreshServerStatus();
  } catch (error) {
    setLog(`Server start error: ${String(error)}`);
  } finally {
    startServerBtn.disabled = false;
  }
}

generateBtn.addEventListener("click", () => {
  void generateAndPlay();
});

refreshBtn.addEventListener("click", () => {
  frameEl.src = latestPlayUrl;
  svgFrameEl.src = latestSvgUrl;
  void refreshServerStatus();
  void refreshLevels();
});

startServerBtn.addEventListener("click", () => {
  void startServer();
});

void loadInitial();

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
