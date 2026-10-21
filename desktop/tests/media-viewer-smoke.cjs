const {_electron: electron} = require('../../web/node_modules/playwright');
const assert = require('node:assert/strict');
const {mkdtemp, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const executablePath = require('../node_modules/electron');

(async () => {
    const root = resolve(__dirname, '../..');
    const directory = await mkdtemp(join(tmpdir(), 'aigram-media-smoke-'));
    const application = await electron.launch({
        executablePath, args: [join(__dirname, 'fixtures/startup-updater.cjs'), '--startup'],
        env: {...process.env, BOLTUNYA_UPDATER_ROOT: root, AIGRAM_DATA_DIR: directory, AIGRAM_SKIP_UPDATE_CHECK: 'false'}, timeout: 30_000,
    });
    try {
        const updater = await application.firstWindow();
        await updater.getByRole('status').filter({hasText: 'Проверяем обновления'}).waitFor();
        const mainPromise = application.waitForEvent('window');
        await application.evaluate(() => {globalThis.__finishUpdateCheck();});
        const main = await mainPromise;
        await main.locator('[data-qa="startup-main"]').waitFor();
        await updater.waitForEvent('close');
        const next = application.waitForEvent('window');
        await main.evaluate(() => window.aigramDesktop.openMediaViewer({chatId: '-100123', messageId: '456'}));
        const viewer = await next;
        await viewer.locator('[data-qa="native-media-viewer"]').waitFor();
        const url = new URL(viewer.url());
        assert.equal(url.search, '?chatId=-100123&messageId=456');
        const state = await viewer.evaluate(() => window.aigramDesktop.state());
        assert.equal(state.role, 'media-viewer');
        assert.equal(state.maximized, true);
        assert.equal(state.fullscreen, false);
        const geometry = await application.evaluate(({BrowserWindow, screen}) => {
            const window = BrowserWindow.getAllWindows().find(candidate => candidate.webContents.getURL().includes('/media-viewer/'));
            return {content: window.getContentBounds(), area: screen.getDisplayMatching(window.getBounds()).workArea};
        });
        assert.ok(geometry.content.y + geometry.content.height <= geometry.area.y + geometry.area.height + 2, 'Initial maximize must preserve the Windows taskbar');
        await viewer.evaluate(() => window.aigramDesktop.action('fullscreen'));
        await viewer.waitForFunction(async () => (await window.aigramDesktop.state()).fullscreen);
        await viewer.evaluate(() => window.aigramDesktop.action('fullscreen'));
        await viewer.waitForFunction(async () => !(await window.aigramDesktop.state()).fullscreen);
        const closed = viewer.waitForEvent('close');
        await application.evaluate(({BrowserWindow}) => {
            const window = BrowserWindow.getAllWindows().find(candidate => candidate.webContents.getURL().includes('/media-viewer/'));
            window.webContents.sendInputEvent({type: 'keyDown', keyCode: 'Escape'});
        });
        await closed;
        const remaining = await application.evaluate(({BrowserWindow}) => ({count: BrowserWindow.getAllWindows().length, hidden: BrowserWindow.getAllWindows().every(window => !window.isVisible())}));
        assert.deepEqual(remaining, {count: 2, hidden: true});
        const another = application.waitForEvent('window');
        await main.evaluate(() => window.aigramDesktop.openMediaViewer({chatId: '42', messageId: '100'}));
        const reopened = await another;
        const closedByBridge = reopened.waitForEvent('close');
        void reopened.evaluate(() => {void window.aigramDesktop.closeMediaViewer();}).catch(() => {});
        await closedByBridge;
        const rejected = await main.evaluate(async () => {
            try { await window.aigramDesktop.openMediaViewer({chatId: 'https://example.com', messageId: '1'}); return false; }
            catch { return true; }
        });
        assert.equal(rejected, true);
        assert.equal(await application.evaluate(({BrowserWindow}) => BrowserWindow.getAllWindows().length), 2);
        console.log(JSON.stringify({mediaViewerAuthenticated: true, maximizedWithTaskbar: true, fullscreenToggle: true, escapeClosesOnlyViewer: true, bridgeClose: true, arbitraryUrlRejected: true}));
    } finally {
        await application.close();
        await rm(directory, {recursive: true, force: true});
    }
})().catch(error => {console.error(error); process.exitCode = 1;});
