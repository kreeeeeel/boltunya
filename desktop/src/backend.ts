import {spawn, type ChildProcess} from 'node:child_process';
import {readFile, mkdir, stat} from 'node:fs/promises';
import {join, resolve} from 'node:path';

const READY_TIMEOUT_MS = 40_000;
const POLL_INTERVAL_MS = 150;
const SHUTDOWN_TIMEOUT_MS = 25_000;

export class Backend {
    private process?: ChildProcess;
    private base = '';
    private cookie = '';
    private stopping = false;

    constructor(private readonly packaged: boolean, private readonly resources: string, private readonly directory: string) {}

    async start(startup: boolean): Promise<string> {
        await mkdir(this.directory, {recursive: true});
        const runtime = this.packaged ? join(this.resources, 'runtime/bin/java.exe') : process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin/java.exe') : 'java.exe';
        const libs = this.packaged ? join(this.resources, 'backend/lib/*') : resolve('../backend/build/install/backend/lib/*');
        this.process = spawn(runtime, ['-Dfile.encoding=UTF-8', '-Djdk.net.unixdomain.tmpdir=C:/tiho-no-unix-sockets', '-cp', libs, 'ru.aigram.MainKt', ...(startup ? ['--startup'] : [])], {
            windowsHide: true,
            env: {...process.env, AIGRAM_DATA_DIR: this.directory, BOLTUNYA_DATA_DIR: this.directory},
            stdio: 'ignore',
        });
        let failure: Error | undefined;
        this.process.once('error', error => { failure = error; });
        const started = Date.now();
        const deadline = Date.now() + READY_TIMEOUT_MS;
        while (Date.now() < deadline) {
            if (failure) throw failure;
            try {
                const descriptor = join(this.directory, 'launch.url');
                if ((await stat(descriptor)).mtimeMs < started - 500) throw new Error('Waiting for the current backend');
                const url = new URL((await readFile(descriptor, 'utf8')).trim());
                if (url.hostname !== '127.0.0.1' || !url.searchParams.get('key')) throw new Error('Invalid local backend descriptor');
                this.base = url.origin;
                this.cookie = `tiho=${url.searchParams.get('key')}`;
                await this.request('/api/state');
                return url.toString();
            } catch {
                if (this.process.exitCode !== null) throw new Error('Backend exited before startup');
                await new Promise(resolve => setTimeout(resolve, POLL_INTERVAL_MS));
            }
        }
        throw new Error('Backend startup timed out');
    }

    async request<T = Record<string, unknown>>(path: string, method = 'GET', body?: unknown): Promise<T> {
        const response = await fetch(this.base + path, {
            method, headers: {Cookie: this.cookie, 'Content-Type': 'application/json'},
            body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(5000),
        });
        if (!response.ok) throw new Error(`Backend HTTP ${response.status}`);
        return response.json() as Promise<T>;
    }

    async close(): Promise<void> {
        if (this.stopping) return;
        this.stopping = true;
        try { await this.request('/api/quit', 'POST'); }
        catch (error) { console.error('Backend shutdown request failed:', error instanceof Error ? error.message : 'unknown error'); }
        const child = this.process;
        if (child && child.exitCode === null) {
            await Promise.race([new Promise<void>(resolve => child.once('exit', () => resolve())), new Promise<void>(resolve => setTimeout(resolve, SHUTDOWN_TIMEOUT_MS))]);
            if (child.exitCode === null) child.kill();
        }
    }

    onExit(callback: () => void): void {
        this.process?.once('exit', () => { if (!this.stopping) callback(); });
    }
}
