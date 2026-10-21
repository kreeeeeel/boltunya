import {BrowserWindow} from 'electron';
import {readFileSync} from 'node:fs';
import {APP_NAME} from './brand';

export type UpdateStatus = {message: string; version: string; progress?: number};

export class UpdateWindow {
    private window?: BrowserWindow;
    private status: UpdateStatus = {message: 'Проверяем обновления', version: ''};

    async open(preload: string, logo: string, version: string): Promise<void> {
        this.status = {...this.status, version};
        this.window = new BrowserWindow({
            width: 340, height: 340, frame: false, resizable: false, maximizable: false,
            minimizable: false, fullscreenable: false, show: false, backgroundColor: '#152128',
            title: APP_NAME, icon: logo, roundedCorners: true,
            webPreferences: {preload, contextIsolation: true, sandbox: true, nodeIntegration: false, devTools: false},
        });
        this.window.setMenu(null);
        this.window.webContents.setWindowOpenHandler(() => ({action: 'deny'}));
        this.window.webContents.on('will-navigate', event => event.preventDefault());
        const image = readFileSync(logo).toString('base64');
        const html = `<!doctype html><html lang="ru"><head><meta charset="UTF-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline';"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${APP_NAME}</title><style>
            *{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;overflow:hidden}body{font:15px/1.5 'Segoe UI',sans-serif;color:#e8f0f2;background:radial-gradient(ellipse at 50% 20%,#24434b 0,#152128 67%);user-select:none;-webkit-app-region:drag}.content{height:100%;display:flex;flex-direction:column;align-items:center;padding:60px 30px 18px}.logo{width:96px;height:96px;filter:drop-shadow(0 12px 24px #0003)}.status{font-size:17px;font-weight:700;color:#e8f0f2;text-align:center;min-height:42px;display:flex;align-items:center;justify-content:center;margin:34px 0 18px}.track{height:5px;width:100%;border-radius:20px;background:#ffffff12;overflow:hidden;visibility:hidden}.track[data-visible=true]{visibility:visible}.fill{height:100%;width:0;background:#6ab8ad;border-radius:inherit;transition:width .18s ease}.version{font-size:14px;color:#a7bac1;margin:auto 0 0;padding-top:18px}
        </style></head><body><main class="content"><img class="logo" src="data:image/png;base64,${image}" alt=""><p class="status" role="status">Проверяем обновления</p><div class="track" role="progressbar" aria-label="Загрузка обновления" aria-valuemin="0" aria-valuemax="100"><div class="fill"></div></div><p class="version" data-qa="updater-version"></p></main></body></html>`;
        await this.window.loadURL(`data:text/html;charset=utf-8,${encodeURIComponent(html)}`);
        this.update(this.status);
        this.window.show();
    }

    update(status: UpdateStatus): void {
        this.status = status;
        if (this.window && !this.window.isDestroyed()) this.window.webContents.send('updater:status', status);
    }

    close(): void { this.window?.destroy(); this.window = undefined; }
}
