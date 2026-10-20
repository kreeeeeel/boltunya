import {app, BrowserWindow, dialog, ipcMain, shell} from 'electron';
import {join, resolve} from 'node:path';
import {mkdirSync} from 'node:fs';
import {Backend} from './backend';
import {TrayController} from './tray';
import {StartupRegistration} from './startup';
import {StorageLocations} from './storage-locations';
import {UpdateWindow} from './update-window';
import {ApplicationUpdater} from './application-updater';
import {ReleaseClient} from './release-client';
import {PortableUpdate} from './portable-update';

const APP_NAME = 'Болтунья';
const STATE_INTERVAL_MS = 1500;
type State = {telegram: {state: string}; settings: {paused: boolean; closeToTray: boolean; launchAtLogin: boolean}};
let main: BrowserWindow | undefined;
let backend: Backend | undefined;
let tray: TrayController | undefined;
let state: State | undefined;
let quitting = false;
let backgroundStartup = process.argv.includes('--startup');
let decidingClose = false;
let timer: ReturnType<typeof setTimeout> | undefined;
let updateWindow: UpdateWindow | undefined;
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
    app.on('second-instance', (_event, commandLine) => { if (!commandLine.includes('--startup')) show(); });
    app.on('activate', () => show());
    void app.whenReady().then(start).catch(async error => {
        dialog.showErrorBox(APP_NAME, error instanceof Error ? error.message : 'Не удалось запустить приложение.');
        await shutdown();
    });
}

function show(): void { if (!main || main.isDestroyed()) return; backgroundStartup = false; if (main.isMinimized()) main.restore(); main.show(); main.focus(); tray?.hide(); }
function windowState() { return {available: true, maximized: main?.isMaximized() ?? false, visible: main?.isVisible() ?? false, minimized: main?.isMinimized() ?? false}; }

async function start(): Promise<void> {
    const directory = process.env.BOLTUNYA_DATA_DIR ?? process.env.TIHO_DATA_DIR ?? join(app.getPath('appData'), '..', 'Local', 'Tiho');
    const startup = process.argv.includes('--startup');
    const preload = join(__dirname, 'preload.js');
    const image = app.isPackaged ? join(process.resourcesPath, 'boltunya.png') : resolve('assets/boltunya.png');
    let portableUpdate: PortableUpdate | undefined;
    if (app.isPackaged && process.env.BOLTUNYA_SKIP_UPDATE_CHECK !== 'true') {
        updateWindow = new UpdateWindow();
        await updateWindow.open(join(__dirname, 'updater-preload.js'), image, app.getVersion());
        const updatesDirectory = join(directory, 'desktop', 'updates');
        portableUpdate = new PortableUpdate(join(process.resourcesPath, 'apply-update.ps1'), updatesDirectory);
        if (process.argv.includes('--update-failed')) updateWindow?.update({message: 'Не удалось установить обновление. Запускаем предыдущую версию', version: app.getVersion()});
        else if (!process.argv.includes('--updated')) {
            const updater = new ApplicationUpdater(new ReleaseClient(), portableUpdate, updatesDirectory);
            if (await updater.run(app.getVersion(), process.env.PORTABLE_EXECUTABLE_FILE ?? '', startup, value => updateWindow?.update(value))) { await shutdown(); return; }
        } else updateWindow?.update({message: 'Обновление установлено. Запускаем приложение', version: app.getVersion()});
    }
    backend = new Backend(app.isPackaged, process.resourcesPath, directory);
    const url = await backend.start(startup);
    backend.onExit(() => void shutdown());
    state = await backend.request<State>('/api/state');
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
    const notifyWindowState = () => main?.webContents.send('window:changed', windowState());
    main.on('maximize', notifyWindowState);
    main.on('unmaximize', notifyWindowState);
    main.on('show', notifyWindowState);
    main.on('hide', notifyWindowState);
    main.on('minimize', notifyWindowState);
    main.on('restore', notifyWindowState);
    main.webContents.setWindowOpenHandler(({url: target}) => { openExternal(target); return {action: 'deny'}; });
    main.webContents.on('will-navigate', (event, target) => { if (new URL(target).origin !== new URL(url).origin) { event.preventDefault(); openExternal(target); } });
    main.webContents.session.setPermissionRequestHandler((contents, permission, callback) => callback(contents.id === main?.webContents.id && permission === 'clipboard-sanitized-write'));
    ipcMain.handle('window:state', event => { if (event.sender.id !== main?.webContents.id) return {}; return windowState(); });
    ipcMain.handle('window:sync', async event => { if (event.sender.id === main?.webContents.id) await refresh(url); });
    ipcMain.handle('window:quit', event => { if (event.sender.id === main?.webContents.id) void shutdown(); });
    const activeBackend = backend;
    const storage = new StorageLocations(() => activeBackend.request('/api/storage/locations'), path => shell.openPath(path));
    ipcMain.handle('storage:open', async (event, id: unknown) => {
        if (event.sender.id !== main?.webContents.id) throw new Error('Storage access is unavailable from this window');
        await storage.openLocation(id);
    });
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
    await refresh(url);
    if (process.argv.includes('--updated')) {
        try { await portableUpdate?.confirm(process.env.PORTABLE_EXECUTABLE_FILE ?? ''); }
        catch (error) { console.error('Could not confirm the running application update:', error instanceof Error ? error.name : 'unknown error'); }
    }
    if (!startup) show();
    updateWindow?.close();
}

function openExternal(url: string): void { try { if (['https:', 'http:', 'tg:', 'mailto:', 'tel:'].includes(new URL(url).protocol)) void shell.openExternal(url); } catch { console.error('Rejected malformed external link'); } }

async function refresh(url: string): Promise<void> {
    if (quitting) return;
    try {
        const previous = state?.telegram.state;
        state = await backend?.request<State>('/api/state');
        if (quitting) return;
        if (previous === 'ready' && state?.telegram.state !== 'ready' && !backgroundStartup) show();
        if (state?.telegram.state === 'ready' || backgroundStartup) await tray?.connect(url); else tray?.close();
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
    updateWindow?.close();
    tray?.close();
    await backend?.close();
    main?.destroy();
    app.quit();
}
app.on('before-quit', event => { if (!quitting) { event.preventDefault(); void shutdown(); } });
app.on('window-all-closed', () => { if (!quitting && state?.telegram.state !== 'ready') void shutdown(); });
