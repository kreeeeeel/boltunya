import {contextBridge, ipcRenderer} from 'electron';

const windowActions = new Set(['show', 'minimize', 'maximize', 'fullscreen', 'close']);
const trayActions = new Set(['show', 'pause', 'settings', 'quit', 'hide']);
const desktop = {
    state: () => ipcRenderer.invoke('window:state'),
    sync: () => ipcRenderer.invoke('window:sync'),
    quit: () => ipcRenderer.invoke('window:quit'),
    openStorageLocation: (id: string) => ipcRenderer.invoke('storage:open', id),
    action: (action: string) => windowActions.has(action) ? ipcRenderer.invoke('window:action', action) : Promise.reject(new Error('Unsupported window action')),
    onState: (callback: (value: unknown) => void) => {
        const listener = (_event: Electron.IpcRendererEvent, value: unknown) => callback(value);
        ipcRenderer.on('window:changed', listener);
        return () => ipcRenderer.removeListener('window:changed', listener);
    },
};
const tray = {
    action: (action: string) => {
        if (trayActions.has(action)) return ipcRenderer.invoke('tray:action', action);
        return Promise.reject(new Error('Unsupported tray action'));
    },
};
contextBridge.exposeInMainWorld('aigramDesktop', desktop);
contextBridge.exposeInMainWorld('aigramTray', tray);
contextBridge.exposeInMainWorld('boltunyaDesktop', desktop);
contextBridge.exposeInMainWorld('boltunyaTray', tray);
ipcRenderer.on('open:settings', () => window.dispatchEvent(new Event('aigram-settings')));
ipcRenderer.on('tray:refresh', () => window.dispatchEvent(new Event('aigram-tray-refresh')));
