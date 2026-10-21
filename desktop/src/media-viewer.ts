import {BrowserWindow} from 'electron';
import {APP_NAME} from './brand';
import {mediaViewerUrl} from './media-request';

export class MediaViewer {
    private window?: BrowserWindow;
    private navigation = 0;

    constructor(private readonly preload: string, private readonly icon: string, private readonly openExternal: (url: string) => void) {}

    owns(senderId: number): boolean { return this.window?.webContents.id === senderId; }

    state() {
        return {available: true, role: 'media-viewer', maximized: this.window?.isMaximized() ?? false, fullscreen: this.window?.isFullScreen() ?? false, visible: this.window?.isVisible() ?? false, minimized: this.window?.isMinimized() ?? false};
    }

    async open(origin: string, request: unknown): Promise<void> {
        const target = mediaViewerUrl(origin, request);
        if (!this.window || this.window.isDestroyed()) {
            const window = new BrowserWindow({
                width: 1320, height: 860, minWidth: 700, minHeight: 480, frame: false, show: false,
                fullscreenable: true, backgroundColor: '#0c111b', title: APP_NAME, icon: this.icon,
                webPreferences: {preload: this.preload, contextIsolation: true, sandbox: true, nodeIntegration: false, spellcheck: false},
            });
            this.window = window;
            window.setMenu(null);
            window.on('closed', () => { if (this.window === window) this.window = undefined; });
            const changed = () => window.webContents.send('window:changed', this.state());
            window.on('maximize', changed);
            window.on('unmaximize', changed);
            window.on('show', changed);
            window.on('hide', changed);
            window.on('minimize', changed);
            window.on('restore', changed);
            window.on('enter-full-screen', changed);
            window.on('leave-full-screen', changed);
            window.webContents.on('before-input-event', (event, input) => {
                if (input.type === 'keyDown' && input.key === 'Escape') { event.preventDefault(); window.close(); }
            });
            window.webContents.setWindowOpenHandler(({url}) => { this.openExternal(url); return {action: 'deny'}; });
            window.webContents.on('will-navigate', (event, destination) => {
                const url = new URL(destination);
                if (url.origin !== origin || url.pathname !== '/media-viewer/') { event.preventDefault(); this.openExternal(destination); }
            });
            window.maximize();
        }
        const window = this.window;
        const navigation = ++this.navigation;
        try { await window.loadURL(target); }
        catch (error) { if (window.isDestroyed() || navigation !== this.navigation) return; throw error; }
        if (window.isDestroyed() || navigation !== this.navigation) return;
        if (window.isMinimized()) window.restore();
        window.show();
        window.focus();
    }

    action(action: string): void {
        const window = this.window;
        if (!window) return;
        if (action === 'close') window.close();
        else if (action === 'fullscreen') window.setFullScreen(!window.isFullScreen());
        else if (action === 'minimize') window.minimize();
        else if (action === 'maximize') { if (window.isMaximized()) window.unmaximize(); else window.maximize(); }
        else if (action === 'show') { window.show(); window.focus(); }
    }

    close(): void { this.window?.close(); }
}
