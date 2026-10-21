const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mkdtemp, readFile, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {ApplicationUpdater} = require('../dist/application-updater.js');

test('launches the installed app when no update exists', async () => {
    const statuses = [];
    const updater = new ApplicationUpdater({latest: async () => undefined}, {install: async () => assert.fail('Should not install')}, 'unused');
    assert.equal(await updater.run('2.6.17', 'app.exe', false, value => statuses.push(value)), false);
    assert.equal(statuses.at(-1).message, 'Запускаем приложение');
    assert.equal(statuses[0].version, '2.6.17');
});

test('hands verified downloads to the installer and preserves startup tray mode', async () => {
    const statuses = [], installs = [];
    const update = {version: '2.6.18'}, download = {path: 'verified.exe', checksum: 'a'.repeat(64), version: '2.6.18'};
    const client = {latest: async () => update, download: async (_update, _directory, progress) => { progress({percent: 42}); return download; }};
    const updater = new ApplicationUpdater(client, {install: async (...args) => installs.push(args)}, 'updates');
    assert.equal(await updater.run('2.6.17', 'app.exe', true, value => statuses.push(value)), true);
    assert.deepEqual(installs, [[download, 'app.exe', true]]);
    assert.ok(statuses.some(value => value.progress === 42));
    const downloading = statuses.find(value => value.progress === 42);
    assert.equal(downloading.message, 'Загружаем обновления');
    assert.equal(downloading.version, '2.6.18');
    assert.equal(statuses.at(-1).progress, 100);
});

test('records only a safe failure code and keeps the previous app launchable', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'boltunya-updater-'));
    const statuses = [];
    const updater = new ApplicationUpdater({latest: async () => { throw new Error('token=secret and private path'); }}, {}, directory);
    try {
        assert.equal(await updater.run('2.6.17', 'app.exe', false, value => statuses.push(value)), false);
        const text = await readFile(join(directory, 'last-error.json'), 'utf8');
        assert.ok(!text.includes('secret') && !text.includes('private path'));
        const error = JSON.parse(text);
        assert.equal(error.code, 'UPDATE_CHECK_FAILED');
        assert.equal(error.stage, 'check');
        assert.ok(Number.isFinite(Date.parse(error.failedAt)));
        assert.ok(statuses.at(-1).message.includes('Не удалось проверить'));
    } finally { await rm(directory, {recursive: true, force: true}); }
});
