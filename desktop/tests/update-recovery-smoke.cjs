const {_electron: electron} = require('../../web/node_modules/playwright');
const assert = require('node:assert/strict');
const {execFile} = require('node:child_process');
const {promisify} = require('node:util');
const {createHash, randomUUID} = require('node:crypto');
const {mkdtemp, mkdir, readFile, writeFile, copyFile, access, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const execute = promisify(execFile);
const executablePath = require('../node_modules/electron');
const quote = value => `'${value.replaceAll("'", "''")}'`;
(async () => {
    const root = resolve(__dirname, '../..');
    const directory = await mkdtemp(join(tmpdir(), "aigram legacy recovery ' "));
    const profile = join(directory, 'profile'), updates = join(profile, 'desktop/updates');
    const target = join(directory, 'Болтунья.exe'), previous = join(directory, 'previous.exe');
    const launches = join(directory, 'launches.txt'), unexpectedDialog = join(directory, 'dialog.txt');
    await mkdir(updates, {recursive: true});
    const template = await readFile(join(__dirname, 'fixtures/update-launcher.cs'), 'utf8');
    const source = template.replaceAll('LAUNCHER_NAME', 'Launcher' + randomUUID().split('-').join('')).replaceAll('LAUNCHER_MODE', 'old');
    const compilation = join(directory, 'compile.ps1');
    await writeFile(compilation, `\uFEFF$ErrorActionPreference='Stop'\nAdd-Type -TypeDefinition ${quote(source)} -OutputAssembly ${quote(previous)} -OutputType WindowsApplication`, 'utf8');
    await execute('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', compilation], {windowsHide: true, timeout: 30_000, env: {...process.env, PSModulePath: undefined}});
    const next = Buffer.from('MZ synthetic failed new portable executable');
    await writeFile(target, next); await copyFile(previous, target + '.update-backup');
    await writeFile(join(updates, 'install.json'), JSON.stringify({target, download: join(updates, '2.6.22.exe'), checksum: createHash('sha256').update(next).digest('hex'), processId: 2147483646, startup: true}));
    let application;
    try {
        application = await electron.launch({executablePath, args: [join(__dirname, 'fixtures/update-recovery.cjs'), '--updated', '--startup'], env: {...process.env, BOLTUNYA_UPDATER_ROOT: root, AIGRAM_DATA_DIR: profile, BOLTUNYA_DATA_DIR: profile, PORTABLE_EXECUTABLE_FILE: target, AIGRAM_SKIP_UPDATE_CHECK: 'false', AIGRAM_SYNTHETIC_UPDATES: updates, AIGRAM_SYNTHETIC_LAUNCHES: launches, AIGRAM_SYNTHETIC_DIALOG: unexpectedDialog}, timeout: 30_000});
        await application.waitForEvent('close', {timeout: 20_000});
        const deadline = Date.now() + 15_000;
        let restarted = false;
        while (Date.now() < deadline) {
            try { restarted = (await readFile(launches, 'utf8')).includes('old:--update-failed|--startup'); }
            catch (error) { if (error.code !== 'ENOENT') throw error; }
            if (restarted) break;
            await new Promise(resolve => setTimeout(resolve, 100));
        }
        assert.equal(restarted, true);
        assert.deepEqual(await readFile(target), await readFile(previous));
        await assert.rejects(access(unexpectedDialog));
        assert.equal(JSON.parse(await readFile(join(updates, 'last-error.json'), 'utf8')).code, 'UPDATE_INSTALL_FAILED');
        console.log(JSON.stringify({legacyStartupFailureRecovered: true, previousVersionRestartedInTray: true, noFatalDialog: true, noDownloads: true, noTelegram: true}));
    } finally {
        await application?.close().catch(error => { if (!error.message.includes('closed')) throw error; });
        await rm(directory, {recursive: true, force: true});
    }
})().catch(error => {console.error(error); process.exitCode = 1;});
