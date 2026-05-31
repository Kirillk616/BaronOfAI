const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("baronApp", {
  getInitialPrompt: () => ipcRenderer.invoke("app:getInitialPrompt"),
  getServerStatus: () => ipcRenderer.invoke("app:getServerStatus"),
  startServer: () => ipcRenderer.invoke("app:startServer"),
  listLevels: () => ipcRenderer.invoke("app:listLevels"),
  generateLevel: (prompt) => ipcRenderer.invoke("app:generateLevel", prompt),
});
