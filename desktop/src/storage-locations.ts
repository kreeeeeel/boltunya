import {realpath, stat} from 'node:fs/promises';
import {isAbsolute} from 'node:path';

const LOCATION_IDS = new Set(['application', 'memory', 'telegram-cache', 'ollama-runtime', 'ollama-models', 'speech']);

type Location = {id: string; path: string; openable: boolean; kind: string};

export class StorageLocations {
    constructor(
        private readonly load: () => Promise<{locations: Location[]}>,
        private readonly open: (path: string) => Promise<string>,
    ) {}

    async openLocation(id: unknown): Promise<void> {
        if (typeof id !== 'string' || !LOCATION_IDS.has(id)) throw new Error('Unknown storage location');
        const {locations} = await this.load();
        const location = locations.find(candidate => candidate.id === id);
        if (!location?.openable || location.kind !== 'directory' || !isAbsolute(location.path)) throw new Error('Storage location is unavailable on this computer');
        const directory = await realpath(location.path);
        if (!(await stat(directory)).isDirectory()) throw new Error('Storage location is not a directory');
        const failure = await this.open(directory);
        if (failure) throw new Error('Could not open the storage location');
    }
}
