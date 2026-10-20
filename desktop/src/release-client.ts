import {createHash} from 'node:crypto';
import {createWriteStream} from 'node:fs';
import {mkdir, open, rename, rm, stat} from 'node:fs/promises';
import {join} from 'node:path';
import {Readable, Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';

const REPOSITORY = 'kreeeeeel/boltunya';
const API_ORIGIN = 'https://api.github.com';
const DOWNLOAD_HOSTS = new Set(['api.github.com', 'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com']);
const CHECK_TIMEOUT_MS = 6_000;
const DOWNLOAD_TIMEOUT_MS = 10 * 60_000;
const MAX_RELEASE_BYTES = 1_073_741_824;
const MIN_RELEASE_BYTES = 1_048_576;
const MAX_REDIRECTS = 5;

type Asset = {name: string; size: number; url: string; browser_download_url: string};
type Release = {tag_name: string; draft: boolean; prerelease: boolean; assets: Asset[]};
export type ReleaseUpdate = {version: string; executable: Asset; checksum: Asset};
export type DownloadProgress = {received: number; total: number; percent?: number};
export type DownloadedUpdate = {path: string; checksum: string; version: string};

export class ReleaseClient {
    constructor(private readonly request: typeof fetch = fetch) {}

    async latest(currentVersion: string): Promise<ReleaseUpdate | undefined> {
        const response = await this.fetch(`${API_ORIGIN}/repos/${REPOSITORY}/releases/latest`, 'application/vnd.github+json', CHECK_TIMEOUT_MS);
        if (!response.ok) throw new Error(`Update check failed (HTTP ${response.status})`);
        const release = await response.json() as Release;
        const version = release.tag_name?.replace(/^v/u, '');
        if (release.draft || release.prerelease || !this.newer(version, currentVersion)) return undefined;
        const executable = release.assets?.find(asset => asset.name === 'Boltunya.exe');
        const checksum = release.assets?.find(asset => asset.name === 'SHA256.txt');
        if (!executable || !checksum || executable.size < MIN_RELEASE_BYTES || executable.size > MAX_RELEASE_BYTES) throw new Error('Update release is incomplete');
        return {version, executable, checksum};
    }

    async download(update: ReleaseUpdate, directory: string, progress: (value: DownloadProgress) => void): Promise<DownloadedUpdate> {
        const checksumResponse = await this.fetch(this.assetUrl(update.checksum), 'application/octet-stream', CHECK_TIMEOUT_MS);
        if (!checksumResponse.ok) throw new Error(`Update checksum failed (HTTP ${checksumResponse.status})`);
        const checksumText = await checksumResponse.text();
        if (checksumText.length > 16_384) throw new Error('Update checksum is too large');
        const expected = checksumText.replace(/^\uFEFF/u, '').split(/\r?\n/u).map(line => /^([a-f\d]{64})\s+\*?Boltunya\.exe\s*$/iu.exec(line)).find(Boolean)?.[1]?.toLowerCase();
        if (!expected) throw new Error('Update checksum is invalid');
        await mkdir(directory, {recursive: true});
        const temporary = join(directory, `${update.version}.download`);
        const destination = join(directory, `${update.version}.exe`);
        try {
            const response = await this.fetch(this.assetUrl(update.executable), 'application/octet-stream', DOWNLOAD_TIMEOUT_MS);
            if (!response.ok || !response.body) throw new Error(`Update download failed (HTTP ${response.status})`);
            const hash = createHash('sha256');
            let received = 0;
            let previousPercent = -1;
            const total = update.executable.size;
            const meter = new Transform({transform(chunk: Buffer, _encoding, callback) {
                received += chunk.length;
                if (received > MAX_RELEASE_BYTES || received > total) return callback(new Error('Update download exceeded its declared size'));
                hash.update(chunk);
                const percent = Math.min(100, Math.floor(received * 100 / total));
                if (percent !== previousPercent) { previousPercent = percent; progress({received, total, percent}); }
                callback(null, chunk);
            }});
            progress({received: 0, total, percent: 0});
            await pipeline(Readable.fromWeb(response.body as Parameters<typeof Readable.fromWeb>[0]), meter, createWriteStream(temporary, {flags: 'w'}));
            if (received !== total || (await stat(temporary)).size !== total) throw new Error('Update download is incomplete');
            if (hash.digest('hex') !== expected) throw new Error('Update checksum does not match');
            const file = await open(temporary, 'r');
            try {
                const header = Buffer.alloc(2);
                await file.read(header, 0, 2, 0);
                if (header[0] !== 0x4d || header[1] !== 0x5a) throw new Error('Update is not a Windows executable');
            } finally { await file.close(); }
            await rename(temporary, destination);
            return {path: destination, checksum: expected, version: update.version};
        } catch (error) {
            await rm(temporary, {force: true});
            throw error;
        }
    }

    private assetUrl(asset: Asset): string { return asset.browser_download_url; }

    private newer(candidate: string, current: string): boolean {
        if (!/^\d+\.\d+\.\d+$/u.test(candidate) || !/^\d+\.\d+\.\d+$/u.test(current)) return false;
        const a = candidate.split('.').map(Number), b = current.split('.').map(Number);
        const different = a.findIndex((part, index) => part !== b[index]);
        return different >= 0 && a[different] > b[different];
    }

    private async fetch(target: string, accept: string, timeout: number, redirects = 0): Promise<Response> {
        const url = new URL(target);
        if (url.protocol !== 'https:' || !DOWNLOAD_HOSTS.has(url.hostname) || url.username || url.password) throw new Error('Update URL is not trusted');
        const response = await this.request(url, {
            headers: {Accept: accept, 'User-Agent': 'Boltunya-Updater'},
            signal: AbortSignal.timeout(timeout), redirect: 'manual',
        });
        if ([301, 302, 303, 307, 308].includes(response.status)) {
            if (redirects >= MAX_REDIRECTS) throw new Error('Too many update redirects');
            const location = response.headers.get('location');
            if (!location) throw new Error('Update redirect is missing');
            await response.body?.cancel();
            return this.fetch(new URL(location, url).toString(), accept, timeout, redirects + 1);
        }
        return response;
    }
}
