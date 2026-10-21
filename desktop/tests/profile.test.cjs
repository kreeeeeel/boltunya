const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mkdtemp, mkdir, writeFile, readFile, access, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {DesktopProfile} = require('../dist/profile.js');
const {ownsLegacyStartup} = require('../dist/startup-policy.js');

test('uses the Aigram profile while respecting current and historical explicit directory overrides', () => {
    const appData = join(tmpdir(), 'roaming'), local = join(tmpdir(), 'Local');
    assert.equal(new DesktopProfile({LOCALAPPDATA: local}, appData).dataDirectory(), join(local, 'Aigram'));
    assert.equal(new DesktopProfile({TIHO_DATA_DIR: 'legacy'}, appData).dataDirectory(), 'legacy');
    assert.equal(new DesktopProfile({BOLTUNYA_DATA_DIR: 'previous', TIHO_DATA_DIR: 'legacy'}, appData).dataDirectory(), 'previous');
    const profile = new DesktopProfile({AIGRAM_DATA_DIR: 'current', BOLTUNYA_DATA_DIR: 'previous'}, appData);
    assert.equal(profile.dataDirectory(), 'current');
    assert.equal(profile.userDataDirectory(), join('current', 'desktop'));
});

test('moves data and the previously selected Electron profile to one Aigram root without changing the originals', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-profile-'));
    const local = join(directory, 'Local'), roaming = join(directory, 'Roaming'), source = join(local, 'Tiho');
    const previous = join(roaming, 'Болтунья');
    try {
        await mkdir(join(source, 'memory/100/42'), {recursive: true}); await mkdir(previous, {recursive: true});
        await writeFile(join(source, 'settings.json'), 'synthetic-settings');
        await writeFile(join(source, 'memory/100/42/MEMORY.md'), 'Synthetic manual notes');
        await writeFile(join(previous, 'Preferences'), 'synthetic-browser-preferences');
        const profile = new DesktopProfile({LOCALAPPDATA: local}, roaming);
        profile.prepare();
        assert.equal(profile.dataDirectory(), join(local, 'Aigram'));
        assert.equal(profile.userDataDirectory(), join(local, 'Aigram/desktop/browser'));
        assert.equal(await readFile(join(profile.dataDirectory(), 'settings.json'), 'utf8'), 'synthetic-settings');
        assert.equal(await readFile(join(profile.userDataDirectory(), 'Preferences'), 'utf8'), 'synthetic-browser-preferences');
        assert.equal(await readFile(join(profile.dataDirectory(), 'memory/100/42/MEMORY.md'), 'utf8'), 'Synthetic manual notes');
        assert.equal(await readFile(join(source, 'settings.json'), 'utf8'), 'synthetic-settings');
        profile.prepare();
        const isolated = new DesktopProfile({LOCALAPPDATA: local, AIGRAM_DATA_DIR: join(directory, 'test-profile')}, roaming);
        isolated.prepare();
        assert.equal(isolated.userDataDirectory(), join(directory, 'test-profile/desktop'));
        await assert.rejects(access(join(directory, 'test-profile')));
    } finally { await rm(directory, {recursive: true, force: true}); }
});

test('defers migration for a validated legacy updater handoff and migrates on a later normal launch', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-update-profile-'));
    const local = join(directory, 'Local'), roaming = join(directory, 'Roaming'), source = join(local, 'Tiho');
    const executable = join(directory, 'Aigram.exe');
    try {
        await mkdir(join(source, 'desktop/updates'), {recursive: true}); await writeFile(executable, 'MZ synthetic');
        await writeFile(join(source, 'settings.json'), 'synthetic-owner');
        await writeFile(join(source, 'desktop/updates/install.json'), JSON.stringify({target: executable, checksum: 'a'.repeat(64), nonce: 'session'}));
        const environment = {LOCALAPPDATA: local, PORTABLE_EXECUTABLE_FILE: executable};
        const updated = new DesktopProfile(environment, roaming, ['--updated', '--update-session=session']);
        updated.prepare();
        assert.equal(updated.dataDirectory(), source);
        assert.equal(updated.userDataDirectory(), join(source, 'desktop'));
        await assert.rejects(access(join(local, 'Aigram')));
        const failed = new DesktopProfile(environment, roaming, ['--update-failed']); failed.prepare();
        assert.equal(failed.dataDirectory(), source);
        const normal = new DesktopProfile(environment, roaming); normal.prepare();
        assert.equal(normal.dataDirectory(), join(local, 'Aigram'));
        assert.equal(await readFile(join(normal.dataDirectory(), 'settings.json'), 'utf8'), 'synthetic-owner');
        assert.equal(await readFile(join(normal.dataDirectory(), 'desktop/updates/install.json'), 'utf8'), await readFile(join(source, 'desktop/updates/install.json'), 'utf8'));
    } finally { await rm(directory, {recursive: true, force: true}); }
});

test('migrates only known application launchers and never an unrelated startup command', () => {
    assert.equal(ownsLegacyStartup('Aigram REG_SZ "C:\\Apps\\Aigram.exe" --startup', 'C:\\Apps\\Aigram.exe'), true);
    assert.equal(ownsLegacyStartup('Болтунья REG_SZ "C:\\Downloads\\Болтунья.exe" --startup', 'C:\\Apps\\Aigram.exe'), true);
    assert.equal(ownsLegacyStartup('Boltunya REG_SZ C:\\Downloads\\Boltunya.exe --startup', 'C:\\Apps\\Aigram.exe'), true);
    assert.equal(ownsLegacyStartup('Repli REG_SZ "C:\\Other\\Repli.exe" --startup', 'C:\\Apps\\Aigram.exe'), false);
    assert.equal(ownsLegacyStartup('Repli REG_SZ "C:\\Repli\\application\\Repli.exe" --startup', 'C:\\Apps\\Aigram.exe'), true);
    assert.equal(ownsLegacyStartup('Invalid data', 'C:\\Apps\\Aigram.exe'), false);
});
