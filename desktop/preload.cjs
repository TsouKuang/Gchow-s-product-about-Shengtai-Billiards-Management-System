const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('connection', {
  get: () => ipcRenderer.invoke('get-connection'),
  save: (value) => ipcRenderer.invoke('save-connection', value),
});
contextBridge.exposeInMainWorld('frontDeskSkin', {
  get: () => ipcRenderer.invoke('get-desk-skin'),
  save: (value) => ipcRenderer.invoke('save-desk-skin', value),
});
