const {test} = require('node:test');
const assert = require('node:assert/strict');
const {execFile, spawn} = require('node:child_process');
const {createHash, randomUUID} = require('node:crypto');
const {promisify} = require('node:util');
const {mkdtemp, mkdir, writeFile, readFile, access, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const {PortableUpdate} = require('../dist/portable-update.js');
const execute = promisify(execFile);
const script = resolve(__dirname, '../assets/apply-update.ps1');
const quote = value => `'${value.replaceAll("'", "''")}'`;
const digest = value => createHash('sha256').update(value).digest('hex');
const windows = {skip: process.platform !== 'win32', timeout: 60_000};
async function waitFor(check, timeout = 15_000) {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) { if (await check()) return; await new Promise(resolve => setTimeout(resolve, 100)); }
    assert.fail('Synthetic update did not complete');
}
async function legacy() {
    const directory = await mkdtemp(join(tmpdir(), "aigram install ' "));
    const updates = join(directory, 'updates');
    await mkdir(updates);
    const target = join(directory, 'Болтунья.exe'), download = join(updates, '2.6.22.exe');
    const old = Buffer.from('MZ previous application'), next = Buffer.from('MZ updated application');
    await writeFile(target, next); await writeFile(target + '.update-backup', old);
    const plan = join(updates, 'install.json');
    await writeFile(plan, JSON.stringify({target, download, checksum: digest(next), processId: 2147483646, startup: false}));
    return {directory, updates, target, download, old, next, plan};
}
async function native(mode, startup = false) {
    const fixture = await legacy();
    const template = await readFile(join(__dirname, 'fixtures/update-launcher.cs'), 'utf8');
    const source = kind => template.replaceAll('LAUNCHER_NAME', 'Launcher' + randomUUID().split('-').join('')).replaceAll('LAUNCHER_MODE', kind);
    const compilation = join(fixture.directory, 'compile.ps1');
    await writeFile(compilation, `\uFEFF$ErrorActionPreference = 'Stop'\nAdd-Type -TypeDefinition ${quote(source('old'))} -OutputAssembly ${quote(fixture.target + '.compiled.exe')} -OutputType WindowsApplication\nAdd-Type -TypeDefinition ${quote(source(mode))} -OutputAssembly ${quote(fixture.download)} -OutputType WindowsApplication`, 'utf8');
    await execute('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', compilation], {windowsHide: true, timeout: 30_000, env: {...process.env, PSModulePath: undefined}});
    fixture.old = await readFile(fixture.target + '.compiled.exe'); fixture.next = await readFile(fixture.download);
    await writeFile(fixture.target, fixture.old); await rm(fixture.target + '.update-backup');
    fixture.nonce = randomUUID(); fixture.launches = join(fixture.directory, 'launches.txt');
    fixture.env = {...process.env, PSModulePath: undefined, AIGRAM_SYNTHETIC_UPDATES: fixture.updates, AIGRAM_SYNTHETIC_LAUNCHES: fixture.launches};
    await writeFile(fixture.plan, JSON.stringify({target: fixture.target, download: fixture.download, checksum: digest(fixture.next), originalChecksum: digest(fixture.old), processId: 2147483646, startup, protocol: 2, nonce: fixture.nonce}));
    return fixture;
}
async function run(fixture) {
    try { await execute('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script, '-Plan', fixture.plan], {windowsHide: true, timeout: 40_000, env: fixture.env}); return 0; }
    catch (error) { if (typeof error.code !== 'number') throw error; return error.code; }
}
test('portable helper waits for real application readiness before deleting the backup', windows, async () => {
    const fixture = await native('healthy', true);
    try {
        assert.equal(await run(fixture), 0);
        assert.deepEqual(await readFile(fixture.target), fixture.next);
        assert.ok((await readFile(fixture.launches, 'utf8')).includes('healthy:--updated|--update-session=' + fixture.nonce + '|--startup'));
        await assert.rejects(access(fixture.target + '.update-backup')); await assert.rejects(access(fixture.plan));
    } finally { await new Promise(resolve => setTimeout(resolve, 2200)); await rm(fixture.directory, {recursive: true, force: true}); }
});
test('a real updated process that starts and then crashes restores and launches the previous version', windows, async () => {
    const fixture = await native('crash', true);
    try {
        assert.equal(await run(fixture), 1);
        await waitFor(async () => (await readFile(fixture.launches, 'utf8')).includes('old:--update-failed|--startup'));
        assert.deepEqual(await readFile(fixture.target), fixture.old);
        assert.ok((await readFile(fixture.launches, 'utf8')).includes('crash:--updated|--update-session=' + fixture.nonce));
        assert.equal(JSON.parse(await readFile(join(fixture.updates, 'last-error.json'), 'utf8')).code, 'UPDATE_INSTALL_FAILED');
        await assert.rejects(access(fixture.target + '.update-backup'));
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});
test('helper readiness is acknowledged before the old process is allowed to exit', windows, async () => {
    const fixture = await legacy(); await writeFile(fixture.target, fixture.old); await writeFile(fixture.download, fixture.next);
    try {
        await new PortableUpdate(script, fixture.updates).install({path: fixture.download, checksum: digest(fixture.next), version: '2.6.22'}, fixture.target, true);
        const plan = JSON.parse(await readFile(fixture.plan, 'utf8'));
        const ready = JSON.parse(await readFile(join(fixture.updates, `ready-${plan.nonce}.json`), 'utf8'));
        assert.equal(ready.nonce, plan.nonce); assert.equal(plan.processId, process.pid);
        assert.deepEqual(await readFile(fixture.target), fixture.old);
        await writeFile(join(fixture.updates, `cancelled-${plan.nonce}.json`), JSON.stringify({nonce: plan.nonce}));
        await waitFor(async () => { try { process.kill(ready.processId, 0); return false; } catch (error) { if (error.code === 'ESRCH') return true; throw error; } });
        assert.deepEqual(await readFile(fixture.target), fixture.old);
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});
test('a helper that exits before readiness leaves the installed executable untouched', windows, async () => {
    const fixture = await legacy(), unavailable = join(fixture.directory, 'unavailable.ps1');
    await writeFile(unavailable, 'exit 9'); await writeFile(fixture.download, fixture.old);
    try {
        await assert.rejects(new PortableUpdate(unavailable, fixture.updates).install({path: fixture.download, checksum: digest(fixture.old), version: '2.6.22'}, fixture.target, false), error => error.message.includes('exited before accepting'));
        assert.deepEqual(await readFile(fixture.target), fixture.next);
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});
test('legacy startup confirmation verifies the executable before removing the previous version', windows, async () => {
    const fixture = await legacy();
    try {
        const installer = new PortableUpdate(script, fixture.updates);
        assert.equal(await installer.confirm(fixture.target), true);
        await assert.rejects(access(fixture.target + '.update-backup')); await assert.rejects(access(fixture.plan));
        assert.equal(await installer.confirm(fixture.target), false);
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});
test('a healthy legacy update stays confirmed when its locked backup cannot be cleaned', windows, async () => {
    const fixture = await legacy();
    const command = `$stream=[System.IO.File]::Open(${quote(fixture.target + '.update-backup')},'Open','Read','None');Write-Output 'READY';[Console]::In.ReadLine()|Out-Null;$stream.Dispose()`;
    const lock = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], {windowsHide: true, env: {...process.env, PSModulePath: undefined}});
    const closed = new Promise(resolve => lock.once('close', resolve));
    try {
        await new Promise((resolve, reject) => { lock.stdout.once('data', value => value.toString().includes('READY') ? resolve() : reject(new Error('Could not lock the synthetic backup'))); lock.once('error', reject); });
        assert.equal(await new PortableUpdate(script, fixture.updates).confirm(fixture.target), true);
        await access(fixture.plan);
        assert.equal(JSON.parse(await readFile(join(fixture.updates, 'last-error.json'), 'utf8')).code, 'UPDATE_INSTALL_FAILED');
    } finally { lock.stdin.end('\n'); await closed; await rm(fixture.directory, {recursive: true, force: true}); }
});

test('a failed startup installed by a legacy helper restores the backup after its parent exits', windows, async () => {
    const fixture = await native('healthy', true);
    const child = join(fixture.directory, 'recover.cjs');
    try {
        await writeFile(fixture.target, fixture.next);
        await writeFile(fixture.target + '.update-backup', fixture.old);
        await writeFile(fixture.plan, JSON.stringify({target: fixture.target, download: fixture.download, checksum: digest(fixture.next), processId: 2147483646, startup: true}));
        await writeFile(child, `const {PortableUpdate}=require(process.argv[2]);new PortableUpdate(process.argv[3],process.argv[4]).recover(process.argv[5]).then(success=>process.exit(success?0:1));`);
        await execute(process.execPath, [child, resolve(__dirname, '../dist/portable-update.js'), script, fixture.updates, fixture.target], {windowsHide: true, timeout: 15_000, env: fixture.env});
        await waitFor(async () => { try { return (await readFile(fixture.launches, 'utf8')).includes('old:--update-failed|--startup'); } catch (error) { if (error.code === 'ENOENT') return false; throw error; } });
        assert.deepEqual(await readFile(fixture.target), fixture.old);
        assert.equal(JSON.parse(await readFile(join(fixture.updates, 'last-error.json'), 'utf8')).code, 'UPDATE_INSTALL_FAILED');
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});

test('new protocol confirmation only acknowledges the matching verified startup session', windows, async () => {
    const fixture = await legacy(), nonce = randomUUID();
    try {
        const configuration = JSON.parse(await readFile(fixture.plan, 'utf8'));
        await writeFile(fixture.plan, JSON.stringify({...configuration, protocol: 2, nonce}));
        const installer = new PortableUpdate(script, fixture.updates);
        assert.equal(await installer.confirm(fixture.target, randomUUID()), false);
        await assert.rejects(access(join(fixture.updates, `healthy-${nonce}.json`)));
        assert.equal(await installer.confirm(fixture.target, nonce), true);
        assert.equal(JSON.parse(await readFile(join(fixture.updates, `healthy-${nonce}.json`), 'utf8')).nonce, nonce);
        assert.deepEqual(await readFile(fixture.target + '.update-backup'), fixture.old);
        await writeFile(fixture.target, 'MZ changed executable');
        assert.equal(await installer.confirm(fixture.target, nonce), false);
        await access(fixture.plan);
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});

test('the complete install handoff survives the initiating process exit and confirms a healthy update', windows, async () => {
    const fixture = await native('healthy', true), child = join(fixture.directory, 'install.cjs');
    try {
        await writeFile(child, `const {PortableUpdate}=require(process.argv[2]);const update=JSON.parse(process.argv[6]);new PortableUpdate(process.argv[3],process.argv[4]).install(update,process.argv[5],true).then(()=>process.exit(0));`);
        const update = {path: fixture.download, checksum: digest(fixture.next), version: '2.6.22'};
        await execute(process.execPath, [child, resolve(__dirname, '../dist/portable-update.js'), script, fixture.updates, fixture.target, JSON.stringify(update)], {windowsHide: true, timeout: 15_000, env: fixture.env});
        await waitFor(async () => { try { await access(fixture.plan); return false; } catch (error) { if (error.code === 'ENOENT') return true; throw error; } });
        assert.deepEqual(await readFile(fixture.target), fixture.next);
        assert.ok((await readFile(fixture.launches, 'utf8')).includes('healthy:--updated|--update-session='));
        await assert.rejects(access(fixture.target + '.update-backup'));
    } finally { await new Promise(resolve => setTimeout(resolve, 2200)); await rm(fixture.directory, {recursive: true, force: true}); }
});
