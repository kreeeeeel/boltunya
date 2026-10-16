import {app, BrowserWindow, dialog, ipcMain, shell} from 'electron';
import {join, resolve} from 'node:path';
import {mkdirSync} from 'node:fs';
import {Backend} from './backend';
import {TrayController} from './tray';
import {StartupRegistration} from './startup';

const APP_NAME = 'Болтунья';
const STATE_INTERVAL_MS = 1500;
type State = {telegram: {state: string}; settings: {paused: boolean; closeToTray: boolean; launchAtLogin: boolean}};
let main: BrowserWindow | undefined;
let backend: Backend | undefined;
let tray: TrayController | undefined;
let state: State | undefined;
let quitting = false;
let waitingForStartupLogin = false;
let decidingClose = false;
let timer: ReturnType<typeof setTimeout> | undefined;
const startupRegistration = new StartupRegistration();

app.setName(APP_NAME);
const isolatedDirectory = process.env.BOLTUNYA_DATA_DIR ?? process.env.TIHO_DATA_DIR;
if (isolatedDirectory) {
    const userData = join(isolatedDirectory, 'desktop');
    mkdirSync(userData, {recursive: true});
    app.setPath('userData', userData);
}
if (!app.requestSingleInstanceLock()) app.quit();
else {
    app.on('second-instance', () => show());
    app.on('activate', () => show());
    void app.whenReady().then(start).catch(async error => {
        dialog.showErrorBox(APP_NAME, error instanceof Error ? error.message : 'Не удалось запустить приложение.');
        await shutdown();
    });
}

function show(): void { if (!main || main.isDestroyed()) return; if (main.isMinimized()) main.restore(); main.show(); main.focus(); tray?.hide(); }
function windowState() { return {available: true, maximized: main?.isMaximized() ?? false, visible: main?.isVisible() ?? false, minimized: main?.isMinimized() ?? false}; }

async function start(): Promise<void> {
    const directory = process.env.BOLTUNYA_DATA_DIR ?? process.env.TIHO_DATA_DIR ?? join(app.getPath('appData'), '..', 'Local', 'Tiho');
    const startup = process.argv.includes('--startup');
    backend = new Backend(app.isPackaged, process.resourcesPath, directory);
    const url = await backend.start(startup);
    backend.onExit(() => void shutdown());
    state = await backend.request<State>('/api/state');
    const preload = join(__dirname, 'preload.js');
    const image = app.isPackaged ? join(process.resourcesPath, 'boltunya.png') : resolve('assets/boltunya.png');
    tray = new TrayController(image, preload, show);
    main = new BrowserWindow({
        width: 1320, height: 860, minWidth: 880, minHeight: 580, frame: false, show: false,
        fullscreenable: false, backgroundColor: '#f3f5f7', title: APP_NAME, icon: image,
        webPreferences: {preload, contextIsolation: true, sandbox: true, nodeIntegration: false, spellcheck: false},
    });
    main.setMenu(null);
    main.on('close', event => {
        if (quitting) return;
        event.preventDefault();
        if (decidingClose) return;
        decidingClose = true;
        void backend?.request<State>('/api/state').then(latest => {
            state = latest;
            if (latest.telegram.state === 'ready' && latest.settings.closeToTray) main?.hide();
            else void shutdown();
        }).catch(error => { console.error('Close state unavailable:', error instanceof Error ? error.message : 'unknown error'); void shutdown(); }).finally(() => { decidingClose = false; });
    });
    const updateWindow = () => main?.webContents.send('window:changed', windowState());
    main.on('maximize', updateWindow);
    main.on('unmaximize', updateWindow);
    main.on('show', updateWindow);
    main.on('hide', updateWindow);
    main.on('minimize', updateWindow);
    main.on('restore', updateWindow);
    main.webContents.setWindowOpenHandler(({url: target}) => { openExternal(target); return {action: 'deny'}; });
    main.webContents.on('will-navigate', (event, target) => { if (new URL(target).origin !== new URL(url).origin) { event.preventDefault(); openExternal(target); } });
    main.webContents.session.setPermissionRequestHandler((contents, permission, callback) => callback(contents.id === main?.webContents.id && permission === 'clipboard-sanitized-write'));
    ipcMain.handle('window:state', event => { if (event.sender.id !== main?.webContents.id) return {}; return windowState(); });
    ipcMain.handle('window:sync', async event => { if (event.sender.id === main?.webContents.id) await refresh(url); });
    ipcMain.handle('window:quit', event => { if (event.sender.id === main?.webContents.id) void shutdown(); });
    ipcMain.handle('window:action', (event, action: string) => {
        if (event.sender.id !== main?.webContents.id) return;
        if (action === 'show') show();
        else if (action === 'minimize') main.minimize();
        else if (action === 'maximize') { if (main.isMaximized()) main.unmaximize(); else main.maximize(); }
        else if (action === 'close') main.close();
    });
    ipcMain.handle('tray:action', async (event, action: string) => {
        if (!tray?.owns(event.sender.id)) return;
        tray.hide();
        if (action === 'show') show();
        else if (action === 'settings') { show(); main?.webContents.send('open:settings'); }
        else if (action === 'pause' && state) { const current = await backend?.request<State>('/api/state'); if (current) await backend?.request('/api/settings', 'PATCH', {paused: !current.settings.paused}); await refresh(url); }
        else if (action === 'quit') await shutdown();
    });
    await main.loadURL(url);
    await main.webContents.executeJavaScript("history.replaceState(null, '', location.pathname)");
    waitingForStartupLogin = startup && state.telegram.state === 'connecting';
    await refresh(url);
    if (!startup || !waitingForStartupLogin && state.telegram.state !== 'ready') main.show();
}

function openExternal(url: string): void { try { if (['https:', 'http:', 'tg:', 'mailto:', 'tel:'].includes(new URL(url).protocol)) void shell.openExternal(url); } catch { console.error('Rejected malformed external link'); } }

async function refresh(url: string): Promise<void> {
    if (quitting) return;
    try {
        const previous = state?.telegram.state;
        state = await backend?.request<State>('/api/state');
        if (quitting) return;
        if (waitingForStartupLogin && state?.telegram.state !== 'connecting') { waitingForStartupLogin = false; if (state?.telegram.state !== 'ready') show(); }
        if (previous === 'ready' && state?.telegram.state !== 'ready') show();
        if (state?.telegram.state === 'ready') await tray?.connect(url); else tray?.close();
        if (state) startupRegistration.update(state.settings.launchAtLogin);
    } catch (error) { console.error('Desktop state refresh failed:', error instanceof Error ? error.message : 'unknown error'); }
    clearTimeout(timer);
    if (!quitting) timer = setTimeout(() => void refresh(url), STATE_INTERVAL_MS);
}

async function shutdown(): Promise<void> {
    if (quitting) return;
    quitting = true;
    clearTimeout(timer);
    main?.hide();
    tray?.close();
    await backend?.close();
    main?.destroy();
    app.quit();
}
app.on('before-quit', event => { if (!quitting) { event.preventDefault(); void shutdown(); } });
app.on('window-all-closed', () => { if (!quitting && state?.telegram.state !== 'ready') void shutdown(); });
