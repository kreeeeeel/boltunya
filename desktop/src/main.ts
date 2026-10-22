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
import {APP_ID, APP_NAME, ICON_FILE} from './brand';
import {DesktopProfile} from './profile';

const STATE_INTERVAL_MS = 5000;
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
let portableUpdate: PortableUpdate | undefined;
let updateConfirmed = false;
let handlingStartupFailure = false;
const updateSession = process.argv.find(argument => argument.startsWith('--update-session='))?.slice('--update-session='.length);
const startupRegistration = new StartupRegistration();

const profile = new DesktopProfile(process.env, app.getPath('appData'), process.argv);
app.setName(APP_NAME);
app.setAppUserModelId(APP_ID);
try {
    profile.prepare();
    const userData = profile.userDataDirectory();
    mkdirSync(userData, {recursive: true});
    app.setPath('userData', userData);
    if (!app.requestSingleInstanceLock()) app.quit();
    else {
        app.on('second-instance', (_event, commandLine) => { if (!commandLine.includes('--startup')) show(); });
        app.on('activate', () => show());
        void app.whenReady().then(start).catch(startupFailure);
    }
} catch (error) {
    void app.whenReady().then(() => { dialog.showErrorBox(APP_NAME, error instanceof Error ? error.message : 'Не удалось перенести профиль приложения.'); app.quit(); });
}

function show(): void { if (!main || main.isDestroyed()) return; backgroundStartup = false; if (main.isMinimized()) main.restore(); main.show(); main.focus(); tray?.hide(); }
function windowState() { return {available: true, role: 'main', maximized: main?.isMaximized() ?? false, fullscreen: false, visible: main?.isVisible() ?? false, minimized: main?.isMinimized() ?? false}; }

async function start(): Promise<void> {
    const directory = profile.dataDirectory();
    const startup = process.argv.includes('--startup');
    const preload = join(__dirname, 'preload.js');
    const image = app.isPackaged ? join(process.resourcesPath, ICON_FILE) : resolve('assets', ICON_FILE);
    if (app.isPackaged) portableUpdate = new PortableUpdate(join(process.resourcesPath, 'apply-update.ps1'), join(directory, 'desktop', 'updates'));
    if (process.argv.includes('--updated') && updateSession) await portableUpdate?.started(process.env.PORTABLE_EXECUTABLE_FILE ?? '', updateSession);
    if (app.isPackaged && (process.env.AIGRAM_SKIP_UPDATE_CHECK ?? process.env.BOLTUNYA_SKIP_UPDATE_CHECK) !== 'true') {
        updateWindow = new UpdateWindow();
        await updateWindow.open(join(__dirname, 'updater-preload.js'), image, app.getVersion());
        const updatesDirectory = join(directory, 'desktop', 'updates');
        if (process.argv.includes('--update-failed')) updateWindow?.update({message: 'Не удалось установить обновление. Запускаем предыдущую версию', version: app.getVersion()});
        else if (!process.argv.includes('--updated')) {
            const updater = new ApplicationUpdater(new ReleaseClient(), portableUpdate!, updatesDirectory);
            if (await updater.run(app.getVersion(), process.env.PORTABLE_EXECUTABLE_FILE ?? '', startup, value => updateWindow?.update(value))) { await shutdown(); return; }
        } else updateWindow?.update({message: 'Обновление установлено. Запускаем приложение', version: app.getVersion()});
    }
    backend = new Backend(app.isPackaged, process.resourcesPath, directory);
    const url = await backend.start(startup);
    backend.onExit(() => { if (!updateConfirmed && process.argv.includes('--updated')) void startupFailure(new Error('Updated backend exited before readiness')); else void shutdown(); });
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
    ipcMain.handle('window:state', event => {
        if (event.sender.id !== main?.webContents.id) return {};
        return windowState();
    });
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
        if (!await portableUpdate?.confirm(process.env.PORTABLE_EXECUTABLE_FILE ?? '', updateSession)) throw new Error('Could not confirm the running application update');
    }
    updateConfirmed = true;
    if (!startup) show();
    updateWindow?.close();
}

async function startupFailure(error: unknown): Promise<void> {
    if (handlingStartupFailure || quitting) return;
    handlingStartupFailure = true;
    console.error('Application startup failed:', error instanceof Error ? error.name : 'unknown error');
    const recovering = process.argv.includes('--updated') && !updateConfirmed && await portableUpdate?.recover(process.env.PORTABLE_EXECUTABLE_FILE ?? '', updateSession);
    if (!recovering) dialog.showErrorBox(APP_NAME, error instanceof Error ? error.message : 'Не удалось запустить приложение.');
    await shutdown();
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
