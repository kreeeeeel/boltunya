import {BrowserWindow, Tray, screen} from 'electron';
import {APP_NAME} from './brand';

const MENU_WIDTH = 226;
const MENU_HEIGHT = 146;

export class TrayController {
    private icon?: Tray;
    private menu?: BrowserWindow;

    constructor(private readonly image: string, private readonly preload: string, private readonly open: () => void) {}

    async connect(url: string): Promise<void> {
        if (this.icon) return;
        this.icon = new Tray(this.image);
        this.icon.setToolTip(APP_NAME);
        this.icon.on('double-click', this.open);
        this.menu = new BrowserWindow({
            width: MENU_WIDTH, height: MENU_HEIGHT, frame: false, resizable: false, show: false,
            skipTaskbar: true, alwaysOnTop: true, backgroundColor: '#ffffff',
            webPreferences: {preload: this.preload, contextIsolation: true, sandbox: true, nodeIntegration: false},
        });
        this.menu.on('blur', () => this.hide());
        this.menu.webContents.setWindowOpenHandler(() => ({action: 'deny'}));
        await this.menu.loadURL(new URL('/tray/', url).toString());
        const measured = await this.menu.webContents.executeJavaScript(`new Promise(resolve => {
            const deadline = Date.now() + 5000;
            const measure = () => {
                const menu = document.querySelector('[data-qa="tray-menu"]');
                if (menu) resolve(Math.ceil(menu.getBoundingClientRect().height));
                else if (Date.now() < deadline) setTimeout(measure, 50);
                else resolve(${MENU_HEIGHT});
            };
            measure();
        })`);
        if (this.menu?.isDestroyed()) return;
        if (Number.isFinite(measured)) this.menu?.setSize(MENU_WIDTH, Math.max(130, Math.min(220, measured)));
        const show = () => {
            const menuHeight = this.menu?.getSize()[1] ?? MENU_HEIGHT;
            const position = screen.getCursorScreenPoint();
            const area = screen.getDisplayNearestPoint(position).workArea;
            this.menu?.setPosition(Math.max(area.x, Math.min(position.x - MENU_WIDTH / 2, area.x + area.width - MENU_WIDTH)), Math.max(area.y, Math.min(position.y - menuHeight, area.y + area.height - menuHeight)));
            this.menu?.webContents.send('tray:refresh');
            this.menu?.show();
        };
        this.icon.on('click', show);
        this.icon.on('right-click', show);
    }

    hide(): void { this.menu?.hide(); }
    owns(id: number): boolean { return this.menu?.webContents.id === id; }
    close(): void { this.icon?.destroy(); this.icon = undefined; this.menu?.destroy(); this.menu = undefined; }
}
