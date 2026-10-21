import {existsSync} from 'node:fs';
import {join} from 'node:path';

const LEGACY_ELECTRON_PROFILES = ['Болтунья', 'boltunya-desktop', 'Boltunya'];

export class DesktopProfile {
    constructor(private readonly environment: NodeJS.ProcessEnv, private readonly appData: string) {}

    dataDirectory(): string {
        return this.environment.AIGRAM_DATA_DIR ?? this.environment.BOLTUNYA_DATA_DIR ?? this.environment.TIHO_DATA_DIR ?? join(this.appData, '..', 'Local', 'Tiho');
    }

    userDataDirectory(): string {
        if (this.environment.AIGRAM_DATA_DIR || this.environment.BOLTUNYA_DATA_DIR || this.environment.TIHO_DATA_DIR) return join(this.dataDirectory(), 'desktop');
        return LEGACY_ELECTRON_PROFILES.map(name => join(this.appData, name)).find(path => existsSync(path)) ?? join(this.dataDirectory(), 'desktop');
    }
}
