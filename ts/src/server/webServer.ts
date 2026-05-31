import express from "express";
import { resolve } from "node:path";
import { statSync } from "node:fs";
import { FileLevelStorage } from "../storage/fileLevelStorage.js";

const app = express();
const port = 8080;
const tsRoot = process.cwd();
const repoRoot = resolve(tsRoot, "..");
const storage = new FileLevelStorage("../WADTool/data/levels");
const webDoomDir = resolve(repoRoot, "WADTool", "data", "webdoom");

app.get("/", (_req, res) => {
  res.status(200).type("html").send(renderHomePage());
});

app.get("/levels", (_req, res) => {
  const items = storage.list();
  const html = [
    '<div id="level-list">',
    ...items.map((meta) => {
      const prompt = escapeHtml(meta.prompt.slice(0, 160));
      return `<div class="item" hx-get="/levels/${meta.id}/view" hx-target="#preview" hx-swap="innerHTML"><strong>${meta.id}</strong><div class="prompt">${prompt}</div></div>`;
    }),
    items.length === 0 ? "<div>No levels yet. Generate one to get started.</div>" : "",
    "</div>",
  ].join("");
  res.status(200).type("html").send(html);
});

app.get("/levels/:id/view", (req, res) => {
  const id = req.params.id;
  const prompt = storage.getPrompt(id);
  if (!prompt) return res.status(404).send("Not found");
  const html = `
    <div>
      <h3>Level ${escapeHtml(id)}</h3>
      <p>${escapeHtml(prompt)}</p>
      <div style="border:1px solid #ddd; height:75vh; overflow:auto;">
        <object data="/levels/${encodeURIComponent(id)}/svg" type="image/svg+xml" style="width:100%; height:100%"></object>
      </div>
      <p><a href="/levels/${encodeURIComponent(id)}/wad">Download WAD</a> | <a href="/levels/${encodeURIComponent(id)}/play">Play in Browser</a></p>
    </div>
  `;
  res.status(200).type("html").send(html);
});

app.get("/levels/:id/svg", (req, res) => {
  const svg = storage.getSvg(req.params.id);
  if (!svg) return res.status(404).send("Not found");
  res.type("image/svg+xml").send(svg);
});

app.get("/levels/:id/wad", (req, res) => {
  const wadPath = storage.getWad(req.params.id);
  if (!wadPath) return res.status(404).send("Not found");
  res.download(wadPath, `${req.params.id}.wad`);
});

app.get("/levels/:id/pwad", (req, res) => {
  const wadPath = storage.getWad(req.params.id);
  if (!wadPath) return res.status(404).send("Not found");
  res.sendFile(wadPath);
});

app.get("/levels/:id/play", (req, res) => {
  const id = req.params.id;
  const prompt = storage.getPrompt(id);
  const wadPath = storage.getWad(id);
  if (!prompt || !wadPath) return res.status(404).send("Not found");
  let wadSize = 0;
  try {
    wadSize = statSync(wadPath).size;
  } catch {
    wadSize = 0;
  }
  res.status(200).type("html").send(renderPlayPage(id, prompt, wadSize));
});

app.use("/webdoom", express.static(webDoomDir));

app.listen(port, "127.0.0.1", () => {
  console.log(`Responding at http://127.0.0.1:${port}`);
});

function renderHomePage(): string {
  return `
<!doctype html>
<html>
<head>
  <meta charset="utf-8"/>
  <title>Doom Levels</title>
  <script src="https://unpkg.com/htmx.org@1.9.12"></script>
  <style>
    body { margin:0; font-family: Arial, sans-serif; }
    .layout { display:flex; height: 100vh; }
    .left { width: 320px; border-right: 1px solid #ddd; overflow:auto; }
    .right { flex:1; padding: 12px; }
    .item { padding: 8px 10px; border-bottom: 1px solid #eee; cursor:pointer; }
    .item:hover { background:#f5f5f5; }
    .prompt { color:#555; font-size: 12px; margin-top: 4px; }
    .toolbar { padding: 8px; border-bottom: 1px solid #eee; }
  </style>
</head>
<body>
  <div class="layout">
    <div class="left">
      <div class="toolbar"><h2>Recent Levels</h2></div>
      <div id="level-list" hx-get="/levels" hx-trigger="load" hx-target="#level-list" hx-swap="outerHTML">Loading levels...</div>
    </div>
    <div class="right">
      <h2>Preview</h2>
      <div id="preview"><p>Select a level from the left to preview its SVG and download WAD.</p></div>
    </div>
  </div>
</body>
</html>
  `.trim();
}

function renderPlayPage(id: string, prompt: string, wadSize: number): string {
  return `
<!doctype html>
<html>
<head>
  <title>Play ${escapeHtml(id)}</title>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
  <style>
    html, body { margin:0; width:100%; height:100%; background:#17212b; color:#f6f9fc; font-family: Arial, sans-serif; overflow:hidden; }
    .shell { display:grid; grid-template-rows:auto 1fr; width:100%; height:100%; }
    .bar { display:flex; gap:12px; align-items:center; justify-content:space-between; padding:10px 12px; background:#253241; border-bottom:1px solid #415267; }
    .title { min-width:0; }
    .title strong { display:block; font-size:15px; }
    .title span { display:block; color:#d6dfeb; font-size:12px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:70vw; }
    .actions { display:flex; gap:8px; align-items:center; }
    .actions a, .actions button { color:#f2f7fc; background:#304055; border:1px solid #4c6078; padding:7px 10px; text-decoration:none; cursor:pointer; font-size:13px; }
    .stage { position:relative; min-height:0; display:grid; place-items:center; background:#101a24; }
    canvas { width:100%; height:100%; max-width:100vw; max-height:calc(100vh - 58px); object-fit:contain; image-rendering:pixelated; outline:none; }
    #status { position:absolute; left:12px; bottom:12px; right:12px; color:#f5f8fc; background:rgba(13,22,33,.84); border:1px solid #4a5e73; padding:10px; font-size:13px; line-height:1.4; }
    #status.complete { display:none; }
  </style>
</head>
<body>
  <div class="shell">
    <div class="bar">
      <div class="title">
        <strong>Playing ${escapeHtml(id)}</strong>
        <span>${escapeHtml(prompt.slice(0, 220))}</span>
      </div>
      <div class="actions">
        <a href="/">Back</a>
        <a href="/levels/${encodeURIComponent(id)}/wad">Download WAD</a>
        <button id="fullscreen">Fullscreen</button>
      </div>
    </div>
    <div class="stage">
      <canvas id="doom" tabindex="0"></canvas>
      <div id="status">Preparing webDOOM runtime...</div>
    </div>
  </div>
  <script>${webDoomBootScript(id, wadSize)}</script>
</body>
</html>
  `.trim();
}

function webDoomBootScript(id: string, wadSize: number): string {
  const pwadUrl = `/levels/${encodeURIComponent(id)}/pwad`;
  return `
  (function () {
    'use strict';
    var status = document.getElementById('status');
    var canvas = document.getElementById('doom');
    var fullscreen = document.getElementById('fullscreen');
    var pwadUrl = '${pwadUrl}';
    var pwadName = 'baron-generated.wad';
    function setStatus(message) {
      status.textContent = message;
      if (!message) status.classList.add('complete');
    }
    function runtimePath(path) {
      var file = path.split('/').pop();
      return '/webdoom/' + file;
    }
    fullscreen.addEventListener('click', function () {
      if (window.Module && Module.requestFullscreen) {
        Module.requestFullscreen(true, false);
      } else if (canvas.requestFullscreen) {
        canvas.requestFullscreen();
      }
    });
    canvas.addEventListener('webglcontextlost', function (event) {
      event.preventDefault();
      setStatus('WebGL context was lost. Reload the page to restart Doom.');
    }, false);
    canvas.addEventListener('contextmenu', function (event) { event.preventDefault(); });
    window.Module = {
      canvas: canvas,
      locateFile: runtimePath,
      arguments: ['-iwad', '/doom2.wad', '-file', '/' + pwadName, '-warp', '1', '-skill', '2'],
      setStatus: function (message) {
        if (!message) { setStatus(''); canvas.focus(); } else { setStatus(message); }
      },
      monitorRunDependencies: function () {},
      preRun: [function () {
        var dependency = 'generated-pwad';
        Module.addRunDependency(dependency);
        setStatus('Loading generated PWAD (${wadSize} bytes)...');
        fetch(pwadUrl)
          .then(function (response) {
            if (!response.ok) throw new Error('HTTP ' + response.status + ' while loading generated WAD.');
            return response.arrayBuffer();
          })
          .then(function (buffer) {
            Module.FS_createDataFile('/', pwadName, new Uint8Array(buffer), true, true, true);
            Module.removeRunDependency(dependency);
          })
          .catch(function (error) {
            console.error(error);
            setStatus('Could not load generated PWAD: ' + error.message);
            Module.removeRunDependency(dependency);
          });
      }]
    };
    var script = document.createElement('script');
    script.src = '/webdoom/doom2.js';
    script.async = true;
    script.onerror = function () {
      setStatus('webDOOM runtime not installed. Put doom2.js, doom2.wasm, and doom2.data in WADTool/data/webdoom.');
    };
    document.body.appendChild(script);
  })();
  `.trim();
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
