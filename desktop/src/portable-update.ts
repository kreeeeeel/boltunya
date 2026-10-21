import {spawn} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {copyFile, mkdir, readFile, realpath, rename, rm, writeFile} from 'node:fs/promises';
import {isAbsolute, join, resolve} from 'node:path';
import type {DownloadedUpdate} from './release-client';

const HELPER_READY_TIMEOUT_MS = 30_000;
const HELPER_POLL_MS = 100;
type Plan = {target: string; download: string; checksum: string; processId: number; startup: boolean; nonce?: string; originalChecksum?: string; protocol?: number};

export class PortableUpdate {
    constructor(private readonly script: string, private readonly directory: string) {}

    async install(update: DownloadedUpdate, executable: string, startup: boolean): Promise<void> {
        this.validateExecutable(executable);
        if (resolve(executable) === resolve(update.path)) throw new Error('Portable executable path is invalid');
        const configuration: Plan = {target: executable, download: update.path, checksum: update.checksum, originalChecksum: await this.hash(executable), processId: process.pid, startup, nonce: randomUUID(), protocol: 2};
        await this.launchHelper(configuration, 'Install');
    }

    async started(executable: string, nonce: string): Promise<void> {
        const configuration = await this.configuration(executable);
        if (!configuration || configuration.protocol !== 2 || configuration.nonce !== nonce) throw new Error('Update startup session is invalid');
        await this.atomicJson(this.marker('started', nonce), {nonce, processId: process.pid});
    }

    async confirm(executable: string, nonce?: string): Promise<boolean> {
        try {
            const configuration = await this.configuration(executable);
            if (!configuration) return false;
            if (await this.hash(executable) !== configuration.checksum) throw new Error('Running update does not match the verified download');
            if (configuration.protocol === 2) {
                if (!nonce || nonce !== configuration.nonce) throw new Error('Update confirmation session is invalid');
                await this.atomicJson(this.marker('healthy', nonce), {nonce});
            } else {
                try {
                    await rm(executable + '.update-backup', {force: true});
                    await rm(join(this.directory, 'install.json'));
                } catch (error) { await this.recordFailure(error); }
            }
            return true;
        } catch (error) { await this.recordFailure(error); return false; }
    }

    /** Legacy helpers do not watch startup health. Recover their backup after a failed new startup. */
    async recover(executable: string, nonce?: string): Promise<boolean> {
        try {
            const configuration = await this.configuration(executable);
            if (!configuration) return false;
            if (configuration.protocol === 2 && nonce && nonce === configuration.nonce) {
                let ready: {nonce?: string; processId?: number} | undefined;
                try { ready = JSON.parse(await readFile(this.marker('ready', nonce), 'utf8')); }
                catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
                if (ready?.nonce === nonce && typeof ready.processId === 'number' && Number.isSafeInteger(ready.processId) && ready.processId > 0) {
                    try { process.kill(ready.processId, 0); return true; }
                    catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ESRCH') throw error; }
                }
            }
            if (await this.hash(executable) !== configuration.checksum) throw new Error('Recovery executable differs from the pending update');
            await this.launchHelper({...configuration, processId: process.pid, nonce: randomUUID(), protocol: 2}, 'Recover');
            return true;
        } catch (error) { await this.recordFailure(error); return false; }
    }

    private async launchHelper(configuration: Plan, mode: 'Install' | 'Recover'): Promise<void> {
        const nonce = configuration.nonce;
        if (!nonce || !this.validNonce(nonce)) throw new Error('Update helper session is invalid');
        await mkdir(this.directory, {recursive: true});
        const script = join(this.directory, `apply-${nonce}.ps1`);
        const plan = join(this.directory, 'install.json');
        await copyFile(this.script, script);
        await this.atomicJson(plan, configuration);
        const argumentsList = ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', `"${script}"`, '-Plan', `"${plan}"`, '-Mode', mode];
        const literal = (value: string) => `'${value.replaceAll("'", "''")}'`;
        const command = `$ErrorActionPreference='Stop'; $helper=Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList @(${argumentsList.map(literal).join(',')}) -WindowStyle Hidden -PassThru; $helper.WaitForExit(); exit $helper.ExitCode`;
        const child = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', Buffer.from(command, 'utf16le').toString('base64')], {
            windowsHide: true, stdio: 'ignore', env: {...process.env, PSModulePath: undefined},
        });
        let ended = false;
        child.once('exit', () => { ended = true; });
        try {
            await new Promise<void>((complete, reject) => { child.once('spawn', complete); child.once('error', reject); });
            const deadline = Date.now() + HELPER_READY_TIMEOUT_MS;
            while (Date.now() < deadline) {
                let ready: {nonce?: string} | undefined;
                try { ready = JSON.parse(await readFile(this.marker('ready', nonce), 'utf8')); }
                catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
                if (ready?.nonce === nonce) { child.unref(); return; }
                if (ended) throw new Error('Update helper exited before accepting the update');
                await new Promise(complete => setTimeout(complete, HELPER_POLL_MS));
            }
            throw new Error('Update helper did not accept the update');
        } catch (error) {
            await this.atomicJson(this.marker('cancelled', nonce), {nonce}).catch(recordingError => console.error('Could not cancel update helper:', recordingError instanceof Error ? recordingError.name : 'unknown error'));
            child.kill();
            throw error;
        }
    }

    private async configuration(executable: string): Promise<Plan | undefined> {
        this.validateExecutable(executable);
        let configuration: Plan;
        try { configuration = JSON.parse(await readFile(join(this.directory, 'install.json'), 'utf8')); }
        catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return undefined; throw error; }
        if (typeof configuration.target !== 'string' || !isAbsolute(configuration.target) || (await realpath(configuration.target)).toLowerCase() !== (await realpath(executable)).toLowerCase()) throw new Error('Pending update belongs to a different executable');
        if (!this.checksum(configuration.checksum)) throw new Error('Pending update checksum is invalid');
        if (configuration.protocol !== undefined && configuration.protocol !== 2) throw new Error('Pending update protocol is unsupported');
        if (configuration.protocol === 2 && (!configuration.nonce || !this.validNonce(configuration.nonce))) throw new Error('Pending update session is invalid');
        return configuration;
    }

    private validateExecutable(value: string): void { if (!isAbsolute(value) || !value.toLowerCase().endsWith('.exe')) throw new Error('Portable executable path is invalid'); }
    private checksum(value: unknown): value is string { return typeof value === 'string' && value.length === 64 && Array.from(value).every(character => character >= '0' && character <= '9' || character >= 'a' && character <= 'f'); }
    private validNonce(value: string): boolean { return value.length === 36 && Array.from(value).every((character, index) => [8, 13, 18, 23].includes(index) ? character === '-' : character >= '0' && character <= '9' || character >= 'a' && character <= 'f'); }
    private marker(kind: string, nonce: string): string { return join(this.directory, `${kind}-${nonce}.json`); }
    private async hash(path: string): Promise<string> { const hash = createHash('sha256'); for await (const chunk of createReadStream(path)) hash.update(chunk); return hash.digest('hex'); }
    private async atomicJson(path: string, value: unknown): Promise<void> { const temporary = path + '.tmp'; await writeFile(temporary, JSON.stringify(value), 'utf8'); await rename(temporary, path); }
    private async recordFailure(error: unknown): Promise<void> {
        console.error('Application update recovery failed:', error instanceof Error ? error.name : 'unknown error');
        try { await writeFile(join(this.directory, 'last-error.json'), JSON.stringify({failedAt: new Date().toISOString(), stage: 'install', code: 'UPDATE_INSTALL_FAILED'}), 'utf8'); }
        catch (recordingError) { console.error('Could not record application update failure:', recordingError instanceof Error ? recordingError.name : 'unknown error'); }
    }
}
