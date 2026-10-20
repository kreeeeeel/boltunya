const {_electron: electron} = require('../../web/node_modules/playwright');
const assert = require('node:assert/strict');
const {mkdtemp, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');

(async () => {
    const root = resolve(__dirname, '../..');
    const directory = await mkdtemp(join(tmpdir(), 'boltunya-startup-updater-'));
    const application = await electron.launch({
        executablePath: join(root, 'desktop/node_modules/electron/dist/electron.exe'),
        args: [join(__dirname, 'fixtures/startup-updater.cjs'), '--startup'],
        env: {...process.env, BOLTUNYA_UPDATER_ROOT: root, BOLTUNYA_DATA_DIR: directory, BOLTUNYA_SKIP_UPDATE_CHECK: 'false'}, timeout: 30_000,
    });
    try {
        const updater = await application.firstWindow();
        await updater.getByRole('status').filter({hasText: 'Проверяем обновления'}).waitFor();
        await updater.waitForFunction(() => Boolean(document.querySelector('[data-qa="updater-version"]').textContent));
        assert.equal(await updater.locator('[data-qa="updater-version"]').textContent(), await application.evaluate(({app}) => app.getVersion()));
        const initial = await application.evaluate(({BrowserWindow}) => ({windows: BrowserWindow.getAllWindows().length, visible: BrowserWindow.getAllWindows()[0].isVisible(), backendStarted: Boolean(globalThis.__startupBackendStarted)}));
        assert.deepEqual(initial, {windows: 1, visible: true, backendStarted: false});
        await application.evaluate(() => {globalThis.__finishUpdateCheck();});
        const main = await application.waitForEvent('window', page => page !== updater);
        await main.locator('[data-qa="startup-main"]').waitFor();
        await updater.waitForEvent('close');
        const ready = await application.evaluate(({BrowserWindow}) => ({windows: BrowserWindow.getAllWindows().length, hidden: BrowserWindow.getAllWindows().every(window => !window.isVisible()), backendStarted: Boolean(globalThis.__startupBackendStarted), tray: BrowserWindow.getAllWindows().some(window => window.webContents.getURL().includes('/tray/'))}));
        assert.deepEqual(ready, {windows: 2, hidden: true, backendStarted: true, tray: true});
        console.log(JSON.stringify({startupUpdaterVisible: initial.visible, mainHiddenAfterUpdateCheck: ready.hidden, trayReady: ready.tray, noReleaseDownloads: true, noTelegram: true}));
    } finally {
        await application.close();
        await rm(directory, {recursive: true, force: true});
    }
})().catch(error => {console.error(error); process.exitCode = 1;});
