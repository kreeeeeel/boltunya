const {test} = require('node:test');
const assert = require('node:assert/strict');
const {execFile, spawn} = require('node:child_process');
const {createHash} = require('node:crypto');
const {promisify} = require('node:util');
const {mkdtemp, mkdir, writeFile, readFile, access, realpath, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const {PortableUpdate} = require('../dist/portable-update.js');

const execute = promisify(execFile);
const script = resolve(__dirname, '../assets/apply-update.ps1');
const quote = value => `'${value.replaceAll("'", "''")}'`;

async function scenario(mode, startup = false) {
    const directory = await mkdtemp(join(tmpdir(), "boltunya install ' "));
    const updates = join(directory, 'updates');
    await mkdir(updates);
    const target = join(directory, 'Болтунья.exe'), download = join(updates, '2.6.18.exe');
    const old = Buffer.from('MZ previous application'), next = Buffer.from('MZ updated application');
    await writeFile(target, old);
    await writeFile(download, next);
    const plan = join(updates, 'install.json'), launches = join(directory, 'launches.jsonl');
    await writeFile(plan, JSON.stringify({target, download, checksum: mode === 'checksum' ? '0'.repeat(64) : createHash('sha256').update(next).digest('hex'), processId: 2147483646, startup}), 'utf8');
    const wrapper = join(directory, 'run.ps1');
    await writeFile(wrapper, `
        $script:launches = 0
        function Start-Process {
            param($FilePath, $ArgumentList, $WorkingDirectory, $WindowStyle)
            $script:launches++
            if (${mode === 'launch' ? '$true' : '$false'} -and $script:launches -eq 1) { throw 'Synthetic launch failure' }
            @{target=$FilePath; arguments=$ArgumentList; windowStyle=$WindowStyle} | ConvertTo-Json -Compress | Add-Content -LiteralPath ${quote(launches)} -Encoding UTF8
        }
        . ${quote(script)} -Plan ${quote(plan)}
        exit $LASTEXITCODE
    `, 'utf8');
    let exitCode = 0;
    try { await execute('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', wrapper], {windowsHide: true, timeout: 30_000, env: {...process.env, PSModulePath: undefined}}); }
    catch (error) { exitCode = error.code; }
    return {directory, updates, target, download, launches, old, next, plan, exitCode};
}

test('portable helper preserves the backup until a verified successful startup confirms the update', {skip: process.platform !== 'win32'}, async () => {
    const fixture = await scenario('success', true);
    try {
        assert.equal(fixture.exitCode, 0);
        assert.deepEqual(await readFile(fixture.target), fixture.next);
        const launch = JSON.parse((await readFile(fixture.launches, 'utf8')).replace(/^\uFEFF/u, '').trim());
        assert.equal(await realpath(launch.target), await realpath(fixture.target));
        assert.deepEqual(launch.arguments, ['--updated', '--startup']);
        assert.equal(launch.windowStyle, 'Hidden');
        assert.deepEqual(await readFile(fixture.target + '.update-backup'), fixture.old);
        await assert.rejects(access(fixture.download));
        await access(fixture.plan);
        const installer = new PortableUpdate(script, fixture.updates);
        assert.equal(await installer.confirm(fixture.target), true);
        await assert.rejects(access(fixture.target + '.update-backup'));
        await assert.rejects(access(fixture.plan));
        assert.equal(await installer.confirm(fixture.target), false);
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});

test('startup confirmation retains the backup if the running file differs from the downloaded update', {skip: process.platform !== 'win32'}, async () => {
    const fixture = await scenario('success');
    try {
        assert.equal(fixture.exitCode, 0);
        await writeFile(fixture.target, 'MZ changed executable');
        assert.equal(await new PortableUpdate(script, fixture.updates).confirm(fixture.target), false);
        assert.deepEqual(await readFile(fixture.target + '.update-backup'), fixture.old);
        await access(fixture.plan);
        const error = JSON.parse(await readFile(join(fixture.updates, 'last-error.json'), 'utf8'));
        assert.equal(error.code, 'UPDATE_INSTALL_FAILED');
    } finally { await rm(fixture.directory, {recursive: true, force: true}); }
});

test('a locked backup records confirmation failure without failing the running application', {skip: process.platform !== 'win32'}, async () => {
    const fixture = await scenario('success');
    const command = `$stream=[System.IO.File]::Open(${quote(fixture.target + '.update-backup')},'Open','Read','None');Write-Output 'READY';[Console]::In.ReadLine()|Out-Null;$stream.Dispose()`;
    const lock = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', command], {windowsHide: true, env: {...process.env, PSModulePath: undefined}});
    const closed = new Promise(resolve => lock.once('close', resolve));
    try {
        await new Promise((resolve, reject) => {
            lock.stdout.once('data', value => value.toString().includes('READY') ? resolve() : reject(new Error('Could not lock the synthetic backup')));
            lock.once('error', reject);
            lock.once('exit', code => { if (code) reject(new Error('Synthetic backup lock failed')); });
        });
        assert.equal(await new PortableUpdate(script, fixture.updates).confirm(fixture.target), false);
        await access(fixture.plan);
        const record = JSON.parse(await readFile(join(fixture.updates, 'last-error.json'), 'utf8'));
        assert.equal(record.code, 'UPDATE_INSTALL_FAILED');
    } finally {
        lock.stdin.end('\n');
        await closed;
        assert.deepEqual(await readFile(fixture.target + '.update-backup'), fixture.old);
        await rm(fixture.directory, {recursive: true, force: true});
    }
});

test('portable helper rejects changed downloads and restores the old executable after launch failure', {skip: process.platform !== 'win32'}, async () => {
    for (const mode of ['checksum', 'launch']) {
        const fixture = await scenario(mode);
        try {
            assert.equal(fixture.exitCode, 1);
            assert.deepEqual(await readFile(fixture.target), fixture.old);
            const record = JSON.parse((await readFile(join(fixture.updates, 'last-error.json'), 'utf8')).replace(/^\uFEFF/u, ''));
            assert.equal(record.stage, 'install');
            assert.equal(record.code, 'UPDATE_INSTALL_FAILED');
            assert.ok(Number.isFinite(Date.parse(record.failedAt)));
            const launch = JSON.parse((await readFile(fixture.launches, 'utf8')).replace(/^\uFEFF/u, '').trim());
            assert.equal(await realpath(launch.target), await realpath(fixture.target));
            assert.deepEqual(launch.arguments, ['--update-failed']);
            await assert.rejects(access(fixture.target + '.update-backup'));
        } finally { await rm(fixture.directory, {recursive: true, force: true}); }
    }
});
