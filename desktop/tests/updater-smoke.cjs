const {_electron: electron} = require('../../web/node_modules/playwright');
const assert = require('node:assert/strict');
const {join, resolve} = require('node:path');

(async () => {
    const root = resolve(__dirname, '../..');
    const application = await electron.launch({
        executablePath: join(root, 'desktop/node_modules/electron/dist/electron.exe'),
        args: [join(__dirname, 'fixtures/updater.cjs')],
        env: {...process.env, BOLTUNYA_UPDATER_ROOT: root}, timeout: 30_000,
    });
    try {
        const page = await application.firstWindow();
        await page.getByRole('status').filter({hasText: 'Загружаем обновления'}).waitFor();
        assert.equal(await page.getByRole('status').textContent(), 'Загружаем обновления');
        assert.equal(await page.locator('[data-qa="updater-version"]').textContent(), '2.6.18');
        const typography = await page.getByRole('status').evaluate(element => ({size: getComputedStyle(element).fontSize, weight: getComputedStyle(element).fontWeight, color: getComputedStyle(element).color}));
        assert.deepEqual(typography, {size: '17px', weight: '700', color: 'rgb(232, 240, 242)'});
        const state = await application.evaluate(({BrowserWindow}) => {
            const windows = BrowserWindow.getAllWindows(), window = windows[0];
            return {count: windows.length, resizable: window.isResizable(), maximizable: window.isMaximizable(), fullscreenable: window.isFullScreenable(), size: window.getSize()};
        });
        assert.deepEqual(state, {count: 1, resizable: false, maximizable: false, fullscreenable: false, size: [340, 340]});
        assert.equal(await page.locator('body').evaluate(element => getComputedStyle(element).webkitAppRegion), 'drag');
        assert.equal(await page.getByRole('progressbar').getAttribute('aria-valuenow'), '42');
        assert.equal(await page.locator('button').count(), 0);
        assert.equal(await page.getByRole('heading').count(), 0);
        await page.waitForFunction(() => {
            const track = document.querySelector('.track').getBoundingClientRect();
            const fill = document.querySelector('.fill').getBoundingClientRect();
            return Math.abs(fill.width - track.width * .42) < 1;
        });
        await page.screenshot({path: process.env.BOLTUNYA_UPDATER_SCREENSHOT ?? join(root, 'docs/resources/updater.png')});
        console.log(JSON.stringify({updaterFixedSize: true, draggable: true, progressPercent: 42, windowControls: false}));
    } finally { await application.close(); }
})().catch(error => {console.error(error); process.exitCode = 1;});
