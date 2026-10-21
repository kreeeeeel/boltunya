import {createHash, randomUUID} from 'node:crypto';
import {closeSync, copyFileSync, existsSync, lstatSync, mkdirSync, openSync, readFileSync, readSync, readdirSync, renameSync, rmdirSync, rmSync, unlinkSync, utimesSync, writeFileSync} from 'node:fs';
import {dirname, isAbsolute, join, relative, resolve, sep} from 'node:path';
import {legacyProfileActive} from './profile-activity';

const MARKER = '.aigram-profile-migration.json';
const TRANSIENT = new Set(['launch.url', 'backend.log', 'engine.log', 'activeapp.lock', 'SingletonLock', 'SingletonCookie', 'SingletonSocket', 'chrome_debug.log']);
type Entry = {path: string; relative: string; directory: boolean; size: number; modified: number};
type Migration = {version: number; state: string; target: string; source?: string; browserSource?: string; fingerprint: string; browserFingerprint: string};

/** Copies only application-owned data. Existing profiles and external Ollama locations are never merged or removed. */
export class ProfileMigration {
    constructor(private readonly target: string, private readonly source?: string, private readonly browserSource?: string) {}

    prepare(): void {
        if (!isAbsolute(this.target)) throw new Error('Каталог профиля Aigram должен быть абсолютным.');
        mkdirSync(dirname(this.target), {recursive: true});
        const lock = this.target + '.migration.lock';
        this.acquire(lock);
        try { this.migrate(); }
        finally { unlinkSync(lock); }
    }

    private migrate(): void {
        const data = this.source && existsSync(this.source) ? this.inventory(this.source, false) : [];
        const browser = this.browserSource && existsSync(this.browserSource) ? this.inventory(this.browserSource, true) : [];
        const configuration: Migration = {version: 1, state: 'copying', target: resolve(this.target), source: this.source && resolve(this.source), browserSource: this.browserSource && resolve(this.browserSource), fingerprint: this.fingerprint(data), browserFingerprint: this.fingerprint(browser)};
        if (this.source && legacyProfileActive(this.source)) throw new Error('Закройте предыдущую версию Aigram перед переносом профиля. Авторизация и файлы не изменены.');
        if (existsSync(this.target)) {
            this.assertDirectory(this.target);
            const marker = join(this.target, MARKER);
            if (existsSync(marker)) {
                const saved = JSON.parse(readFileSync(marker, 'utf8')) as Migration;
                if (!this.sameSources(saved, configuration) || saved.state !== 'complete' || data.some(entry => !entry.directory) && saved.fingerprint !== configuration.fingerprint || browser.some(entry => !entry.directory) && saved.browserFingerprint !== configuration.browserFingerprint) throw new Error('Найдены разные профили Aigram и предыдущей версии. Автоматическое объединение остановлено; исходные данные сохранены.');
                return;
            }
            if (data.length || browser.length) {
                if (readdirSync(this.target).length) throw new Error('Каталог Aigram уже содержит другой профиль. Данные предыдущей версии сохранены отдельно и не объединены.');
            } else return;
        }
        const stage = this.target + '.migration';
        if (existsSync(stage)) {
            this.assertDirectory(stage);
            const marker = join(stage, MARKER);
            if (existsSync(marker) ? !this.sameSources(JSON.parse(readFileSync(marker, 'utf8')), configuration) : readdirSync(stage).some(name => name !== MARKER + '.tmp')) throw new Error('Найдены неизвестные файлы в каталоге переноса Aigram. Исходный профиль сохранён.');
            this.removeStage(stage);
        }
        mkdirSync(stage);
        this.writeMarker(stage, configuration);
        this.copy(data, stage);
        this.copy(browser, join(stage, 'desktop', 'browser'));
        const afterData = this.source && existsSync(this.source) ? this.inventory(this.source, false) : [];
        const afterBrowser = this.browserSource && existsSync(this.browserSource) ? this.inventory(this.browserSource, true) : [];
        if (this.fingerprint(afterData) !== configuration.fingerprint || this.fingerprint(afterBrowser) !== configuration.browserFingerprint || this.source && legacyProfileActive(this.source)) throw new Error('Предыдущий профиль изменился во время переноса. Перенос остановлен; исходные файлы сохранены.');
        this.writeMarker(stage, {...configuration, state: 'complete'});
        if (existsSync(this.target)) {
            if (readdirSync(this.target).length) throw new Error('Другой процесс создал профиль Aigram во время переноса. Исходные файлы сохранены.');
            rmdirSync(this.target);
        }
        renameSync(stage, this.target);
    }

    private inventory(root: string, browser: boolean): Entry[] {
        this.assertDirectory(root);
        const entries: Entry[] = [];
        const visit = (directory: string) => {
            readdirSync(directory, {withFileTypes: true}).forEach(child => {
                const path = join(directory, child.name), name = relative(root, path).split(sep).join('/');
                if (TRANSIENT.has(child.name) || name === MARKER || name === 'updates' && browser) return;
                if (!browser && name.startsWith('desktop/') && name !== 'desktop/updates' && !name.startsWith('desktop/updates/')) return;
                const metadata = lstatSync(path);
                if (metadata.isSymbolicLink()) throw new Error('Профиль содержит ссылку на внешний каталог. Автоматический перенос остановлен; внешние файлы не изменены.');
                if (!metadata.isFile() && !metadata.isDirectory()) throw new Error('Профиль содержит неподдерживаемый файл. Исходные данные сохранены.');
                entries.push({path, relative: name, directory: metadata.isDirectory(), size: metadata.size, modified: metadata.mtimeMs});
                if (metadata.isDirectory()) visit(path);
            });
        };
        visit(root);
        return entries.sort((left, right) => left.relative < right.relative ? -1 : left.relative > right.relative ? 1 : 0);
    }

    private copy(entries: Entry[], target: string): void {
        mkdirSync(target, {recursive: true});
        entries.forEach(entry => {
            const path = join(target, entry.relative);
            if (entry.directory) { mkdirSync(path, {recursive: true}); return; }
            mkdirSync(dirname(path), {recursive: true});
            const digest = this.hash(entry.path);
            copyFileSync(entry.path, path);
            if (this.hash(path) !== digest || this.hash(entry.path) !== digest) throw new Error('Проверка копии профиля не пройдена. Исходные файлы сохранены.');
            utimesSync(path, new Date(entry.modified), new Date(entry.modified));
        });
    }

    private fingerprint(entries: Entry[]): string { return createHash('sha256').update(entries.map(entry => `${entry.relative}\0${entry.directory ? 'directory' : entry.size}\0${entry.directory ? '' : entry.modified}`).join('\n')).digest('hex'); }
    private hash(path: string): string {
        const descriptor = openSync(path, 'r'), buffer = Buffer.allocUnsafe(1_048_576), hash = createHash('sha256');
        try { let bytes: number; while ((bytes = readSync(descriptor, buffer)) > 0) hash.update(buffer.subarray(0, bytes)); return hash.digest('hex'); }
        finally { closeSync(descriptor); }
    }
    private sameSources(saved: Migration, current: Migration): boolean { return saved.version === 1 && saved.target === current.target && saved.source === current.source && saved.browserSource === current.browserSource; }
    private assertDirectory(path: string): void { const metadata = lstatSync(path); if (!metadata.isDirectory() || metadata.isSymbolicLink()) throw new Error('Каталог профиля не является обычной папкой. Исходные данные сохранены.'); }
    private writeMarker(path: string, configuration: Migration): void { const destination = join(path, MARKER), temporary = destination + '.tmp'; writeFileSync(temporary, JSON.stringify(configuration), 'utf8'); renameSync(temporary, destination); }
    private removeStage(stage: string): void { if (resolve(stage) !== resolve(this.target) + '.migration' || dirname(resolve(stage)) !== dirname(resolve(this.target))) throw new Error('Недопустимый каталог переноса.'); rmSync(stage, {recursive: true}); }
    private acquire(path: string): void {
        try { const descriptor = openSync(path, 'wx'); try { writeFileSync(descriptor, JSON.stringify({pid: process.pid, nonce: randomUUID()})); } finally { closeSync(descriptor); } }
        catch (error) {
            if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error;
            const previous = JSON.parse(readFileSync(path, 'utf8')) as {pid: number};
            if (!Number.isSafeInteger(previous.pid) || previous.pid <= 0) throw new Error('Не удалось проверить блокировку переноса Aigram.');
            try { process.kill(previous.pid, 0); }
            catch (status) { if ((status as NodeJS.ErrnoException).code === 'ESRCH') { unlinkSync(path); this.acquire(path); return; } throw status; }
            throw new Error('Перенос профиля Aigram уже выполняется в другом процессе.');
        }
    }
}
