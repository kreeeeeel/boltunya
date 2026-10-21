const {test} = require('node:test');
const assert = require('node:assert/strict');
const {execFile, fork} = require('node:child_process');
const {promisify} = require('node:util');
const fs = require('node:fs');
const {mkdtemp, mkdir, writeFile, readFile, access, rm, symlink} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const {DesktopProfile} = require('../dist/profile.js');
const execute = promisify(execFile);
async function fixture() {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-migration-'));
    const local = join(directory, 'Local'), roaming = join(directory, 'Roaming'), source = join(local, 'Tiho'), target = join(local, 'Aigram');
    const files = ['settings.json', 'telegram/data/100/td.binlog', 'credentials.secret', 'memory/MEMORY.md', 'memory/100/42/MEMORY.md', 'memory/100/42/PARTICIPANTS.json', 'model-profiles.json', 'diagnostics/errors.json', 'activity.json', 'uploads/one/photo.png', 'desktop/Preferences'];
    for (const name of files) { await mkdir(join(source, name, '..'), {recursive: true}); await writeFile(join(source, name), `synthetic:${name}`); }
    return {directory, local, roaming, source, target, files, environment: {LOCALAPPDATA: local}};
}

test('migration preserves every application component and leaves external model weights untouched', async () => {
    const f = await fixture(), outside = join(f.directory, '.ollama/models/model.gguf');
    try {
        await mkdir(join(outside, '..'), {recursive: true}); await writeFile(outside, 'external model');
        const profile = new DesktopProfile(f.environment, f.roaming); profile.prepare();
        for (const name of f.files) {
            const destination = name === 'desktop/Preferences' ? 'desktop/browser/Preferences' : name;
            assert.equal(await readFile(join(f.target, destination), 'utf8'), `synthetic:${name}`);
            assert.equal(await readFile(join(f.source, name), 'utf8'), `synthetic:${name}`);
        }
        assert.equal(await readFile(outside, 'utf8'), 'external model');
        await writeFile(join(f.target, 'settings.json'), 'new settings');
        profile.prepare();
        assert.equal(await readFile(join(f.target, 'settings.json'), 'utf8'), 'new settings');
        await writeFile(join(f.source, 'settings.json'), 'changed legacy profile');
        assert.throws(() => profile.prepare(), error => error.message.includes('разные профили'));
        assert.equal(await readFile(join(f.target, 'settings.json'), 'utf8'), 'new settings');
    } finally { await rm(f.directory, {recursive: true, force: true}); }
});

test('a real interrupted copy keeps the old profile and safely restarts from its own incomplete stage', async () => {
    const f = await fixture(), child = join(f.directory, 'interrupt.cjs');
    try {
        await writeFile(child, `const fs=require('node:fs');const original=fs.copyFileSync;let copied=0;fs.copyFileSync=(...args)=>{original(...args);if(++copied===2)process.exit(17)};const {DesktopProfile}=require(process.argv[2]);new DesktopProfile({LOCALAPPDATA:process.argv[3]},process.argv[4]).prepare();`);
        await assert.rejects(execute(process.execPath, [child, resolve(__dirname, '../dist/profile.js'), f.local, f.roaming], {windowsHide: true}), error => error.code === 17);
        await access(f.target + '.migration'); await assert.rejects(access(f.target));
        assert.equal(await readFile(join(f.source, 'credentials.secret'), 'utf8'), 'synthetic:credentials.secret');
        new DesktopProfile(f.environment, f.roaming).prepare();
        assert.equal(await readFile(join(f.target, 'credentials.secret'), 'utf8'), 'synthetic:credentials.secret');
        await assert.rejects(access(f.target + '.migration'));
        await assert.rejects(access(f.target + '.migration.lock'));
    } finally { await rm(f.directory, {recursive: true, force: true}); }
});

test('copy failure never exposes a partial canonical profile or changes the original', async () => {
    const f = await fixture(), original = fs.copyFileSync;
    try {
        fs.copyFileSync = () => { const error = new Error('Synthetic disk full'); error.code = 'ENOSPC'; throw error; };
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.code === 'ENOSPC');
        await assert.rejects(access(f.target));
        assert.equal(await readFile(join(f.source, 'settings.json'), 'utf8'), 'synthetic:settings.json');
    } finally { fs.copyFileSync = original; await rm(f.directory, {recursive: true, force: true}); }
});

test('refuses to merge an independent existing Aigram profile', async () => {
    const f = await fixture();
    try {
        await mkdir(f.target); await writeFile(join(f.target, 'settings.json'), 'independent profile');
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.message.includes('другой профиль'));
        assert.equal(await readFile(join(f.target, 'settings.json'), 'utf8'), 'independent profile');
    } finally { await rm(f.directory, {recursive: true, force: true}); }
});

test('never follows a junction to external model weights', async () => {
    const f = await fixture(), outside = join(f.directory, 'external-models');
    try {
        await mkdir(outside); await writeFile(join(outside, 'weights'), 'external');
        await symlink(outside, join(f.source, 'linked-models'), process.platform === 'win32' ? 'junction' : 'dir');
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.message.includes('внешний каталог'));
        assert.equal(await readFile(join(outside, 'weights'), 'utf8'), 'external'); await assert.rejects(access(f.target));
    } finally { await rm(f.directory, {recursive: true, force: true}); }
});

test('does not copy an open legacy Telegram profile', async () => {
    const f = await fixture();
    const child = fork(join(__dirname, 'fixtures/profile-owner.cjs'), [f.source], {windowsHide: true, stdio: ['ignore', 'ignore', 'ignore', 'ipc']});
    try {
        await new Promise((resolve, reject) => { child.once('message', resolve); child.once('error', reject); });
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.message.includes('Закройте предыдущую'));
        await assert.rejects(access(f.target));
    } finally { child.kill(); await new Promise(resolve => child.once('exit', resolve)); await rm(f.directory, {recursive: true, force: true}); }
});

test('a connected but slow legacy backend also blocks copying its open Telegram database', async () => {
    const f = await fixture();
    const child = fork(join(__dirname, 'fixtures/profile-owner.cjs'), [f.source, '5000'], {windowsHide: true, stdio: ['ignore', 'ignore', 'ignore', 'ipc']});
    try {
        await new Promise((resolve, reject) => { child.once('message', resolve); child.once('error', reject); });
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.message.includes('Закройте предыдущую'));
        await assert.rejects(access(f.target));
    } finally { child.kill(); await new Promise(resolve => child.once('exit', resolve)); await rm(f.directory, {recursive: true, force: true}); }
});

test('a crash after atomic commit keeps the complete new profile without recopying it', async () => {
    const f = await fixture(), child = join(f.directory, 'after-commit.cjs');
    try {
        await writeFile(child, `const fs=require('node:fs');const original=fs.renameSync;fs.renameSync=(from,to)=>{original(from,to);if(to===process.argv[3])process.exit(18)};const {DesktopProfile}=require(process.argv[2]);new DesktopProfile({LOCALAPPDATA:process.argv[4]},process.argv[5]).prepare();`);
        await assert.rejects(execute(process.execPath, [child, resolve(__dirname, '../dist/profile.js'), f.target, f.local, f.roaming], {windowsHide: true}), error => error.code === 18);
        await access(join(f.target, '.aigram-profile-migration.json'));
        await writeFile(join(f.target, 'settings.json'), 'new-profile-edit');
        new DesktopProfile(f.environment, f.roaming).prepare();
        assert.equal(await readFile(join(f.target, 'settings.json'), 'utf8'), 'new-profile-edit');
        assert.equal(await readFile(join(f.source, 'settings.json'), 'utf8'), 'synthetic:settings.json');
    } finally { await rm(f.directory, {recursive: true, force: true}); }
});

test('an unknown staging directory and an active migration lock are preserved rather than removed', async () => {
    const f = await fixture(), stage = f.target + '.migration', lock = f.target + '.migration.lock';
    try {
        await mkdir(stage); await writeFile(join(stage, 'personal-notes.md'), 'keep');
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.message.includes('неизвестные файлы'));
        assert.equal(await readFile(join(stage, 'personal-notes.md'), 'utf8'), 'keep');
        await writeFile(lock, JSON.stringify({pid: process.pid}));
        assert.throws(() => new DesktopProfile(f.environment, f.roaming).prepare(), error => error.message.includes('другом процессе'));
        assert.equal(JSON.parse(await readFile(lock, 'utf8')).pid, process.pid);
    } finally { await rm(f.directory, {recursive: true, force: true}); }
});

test('removing the retained legacy backup after migration never prevents using the complete Aigram profile', async () => {
    const f = await fixture();
    try {
        const profile = new DesktopProfile(f.environment, f.roaming); profile.prepare();
        await rm(f.source, {recursive:true});
        profile.prepare();
        assert.equal(await readFile(join(f.target,'credentials.secret'),'utf8'),'synthetic:credentials.secret');
    } finally {await rm(f.directory,{recursive:true,force:true});}
});
