import {existsSync, readFileSync, realpathSync} from 'node:fs';
import {join, resolve} from 'node:path';
import {ProfileMigration} from './profile-migration';

const LEGACY_ELECTRON_PROFILES = ['Болтунья', 'boltunya-desktop', 'Boltunya'];

export class DesktopProfile {
    private deferred = false;
    constructor(private readonly environment: NodeJS.ProcessEnv, private readonly appData: string, private readonly argumentsList: readonly string[] = []) {}

    prepare(): void {
        if (this.override()) return;
        if (this.activeLegacyUpdate()) { this.deferred = true; return; }
        new ProfileMigration(this.canonical(), this.legacy(), this.legacyUserData()).prepare();
    }

    dataDirectory(): string { return this.override() ?? (this.deferred ? this.legacy() : this.canonical()); }
    userDataDirectory(): string {
        if (this.override()) return join(this.dataDirectory(), 'desktop');
        return this.deferred ? this.legacyUserData() : join(this.canonical(), 'desktop', 'browser');
    }
    private override(): string | undefined { return this.environment.AIGRAM_DATA_DIR || this.environment.BOLTUNYA_DATA_DIR || this.environment.TIHO_DATA_DIR; }
    private local(): string { return this.environment.LOCALAPPDATA ?? resolve(this.appData, '..', 'Local'); }
    private canonical(): string { return join(this.local(), 'Aigram'); }
    private legacy(): string { return join(this.local(), 'Tiho'); }
    private legacyUserData(): string {
        const candidates = [...LEGACY_ELECTRON_PROFILES.map(name => join(this.appData, name)), join(this.legacy(), 'desktop')];
        const marker = join(this.canonical(), '.aigram-profile-migration.json');
        if (existsSync(marker)) {
            const saved = JSON.parse(readFileSync(marker, 'utf8')) as {browserSource?: string};
            if (saved.browserSource) {
                if (!candidates.includes(saved.browserSource)) throw new Error('Не удалось проверить исходную папку профиля Electron.');
                return saved.browserSource;
            }
        }
        return candidates.find(path => existsSync(path)) ?? join(this.legacy(), 'desktop');
    }
    private activeLegacyUpdate(): boolean {
        if (!this.argumentsList.includes('--updated') && !this.argumentsList.includes('--update-failed')) return false;
        const executable = this.environment.PORTABLE_EXECUTABLE_FILE;
        if (!executable) return false;
        const matches = (directory: string): boolean => {
            const path = join(directory, 'desktop', 'updates', 'install.json');
            if (!existsSync(path)) return false;
            const plan = JSON.parse(readFileSync(path, 'utf8')) as {target?: string; checksum?: string; nonce?: string};
            if (!plan.target || !plan.checksum || plan.checksum.length !== 64 || !Array.from(plan.checksum).every(character => character >= '0' && character <= '9' || character >= 'a' && character <= 'f')) throw new Error('Не удалось проверить состояние обновления перед переносом профиля.');
            const nonce = this.argumentsList.find(argument => argument.startsWith('--update-session='))?.slice('--update-session='.length);
            return realpathSync(plan.target).toLowerCase() === realpathSync(executable).toLowerCase() && (!nonce || nonce === plan.nonce);
        };
        return !matches(this.canonical()) && matches(this.legacy());
    }
}
