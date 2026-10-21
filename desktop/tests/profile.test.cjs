const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mkdtemp, mkdir, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {DesktopProfile} = require('../dist/profile.js');
const {ownsLegacyStartup} = require('../dist/startup-policy.js');

test('preserves the backend profile and accepts both new and historical directory overrides', () => {
    const appData = join(tmpdir(), 'roaming');
    assert.equal(new DesktopProfile({}, appData).dataDirectory(), join(appData, '..', 'Local', 'Tiho'));
    assert.equal(new DesktopProfile({TIHO_DATA_DIR: 'legacy'}, appData).dataDirectory(), 'legacy');
    assert.equal(new DesktopProfile({BOLTUNYA_DATA_DIR: 'previous', TIHO_DATA_DIR: 'legacy'}, appData).dataDirectory(), 'previous');
    const profile = new DesktopProfile({AIGRAM_DATA_DIR: 'current', BOLTUNYA_DATA_DIR: 'previous'}, appData);
    assert.equal(profile.dataDirectory(), 'current');
    assert.equal(profile.userDataDirectory(), join('current', 'desktop'));
});

test('reuses an existing Electron profile so a name change keeps local preferences and the instance lock', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-profile-'));
    try {
        const profile = new DesktopProfile({}, directory);
        assert.equal(profile.userDataDirectory(), join(directory, '..', 'Local', 'Tiho', 'desktop'));
        const previous = join(directory, 'Болтунья');
        await mkdir(previous);
        assert.equal(profile.userDataDirectory(), previous);
        const isolated = new DesktopProfile({AIGRAM_DATA_DIR: join(directory, 'test-profile')}, directory);
        assert.equal(isolated.userDataDirectory(), join(directory, 'test-profile', 'desktop'));
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
