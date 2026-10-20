import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createReadStream} from 'node:fs';
import {copyFile, mkdir, readFile, realpath, rm, writeFile} from 'node:fs/promises';
import {isAbsolute, join, resolve} from 'node:path';
import type {DownloadedUpdate} from './release-client';

export class PortableUpdate {
    constructor(private readonly script: string, private readonly directory: string) {}

    async install(update: DownloadedUpdate, executable: string, startup: boolean): Promise<void> {
        if (!isAbsolute(executable) || !executable.toLowerCase().endsWith('.exe') || resolve(executable) === resolve(update.path)) throw new Error('Portable executable path is invalid');
        await mkdir(this.directory, {recursive: true});
        const script = join(this.directory, 'apply-update.ps1');
        const plan = join(this.directory, 'install.json');
        await copyFile(this.script, script);
        await writeFile(plan, JSON.stringify({target: executable, download: update.path, checksum: update.checksum, processId: process.pid, startup}), 'utf8');
        const child = spawn('powershell.exe', ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script, '-Plan', plan], {
            detached: true, windowsHide: true, stdio: 'ignore', env: {...process.env, PSModulePath: undefined},
        });
        await new Promise<void>((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject); });
        child.unref();
    }

    async confirm(executable: string): Promise<boolean> {
        try { return await this.confirmVerified(executable); }
        catch (error) {
            console.error('Application update confirmation failed:', error instanceof Error ? error.name : 'unknown error');
            try {
                await writeFile(join(this.directory, 'last-error.json'), JSON.stringify({failedAt: new Date().toISOString(), stage: 'install', code: 'UPDATE_INSTALL_FAILED'}), 'utf8');
            } catch (recordingError) { console.error('Could not record update confirmation failure:', recordingError instanceof Error ? recordingError.name : 'unknown error'); }
            return false;
        }
    }

    private async confirmVerified(executable: string): Promise<boolean> {
        const plan = join(this.directory, 'install.json');
        let configuration: {target: string; checksum: string};
        try { configuration = JSON.parse(await readFile(plan, 'utf8')); }
        catch (error) { if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false; throw error; }
        if (!isAbsolute(executable)) throw new Error('Pending update executable path is invalid');
        const target = await realpath(executable);
        if ((await realpath(configuration.target)).toLowerCase() !== target.toLowerCase()) throw new Error('Pending update belongs to a different executable');
        const hash = createHash('sha256');
        for await (const chunk of createReadStream(target)) hash.update(chunk);
        if (hash.digest('hex') !== configuration.checksum) throw new Error('Running update does not match the verified download');
        await rm(target + '.update-backup', {force: true});
        await rm(plan);
        return true;
    }
}
