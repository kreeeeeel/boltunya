import {mkdir, writeFile} from 'node:fs/promises';
import {join} from 'node:path';
import {ReleaseClient} from './release-client';
import {PortableUpdate} from './portable-update';
import type {UpdateStatus} from './update-window';

export class ApplicationUpdater {
    constructor(
        private readonly client: ReleaseClient,
        private readonly installer: PortableUpdate,
        private readonly directory: string,
    ) {}

    async run(version: string, executable: string, startup: boolean, status: (value: UpdateStatus) => void): Promise<boolean> {
        let stage = 'check';
        try {
            status({message: 'Проверяем обновления', version});
            const update = await this.client.latest(version);
            if (!update) { status({message: 'Запускаем приложение', version}); return false; }
            stage = 'download';
            const downloaded = await this.client.download(update, this.directory, value => status({message: 'Загружаем обновления', version: update.version, progress: value.percent}));
            stage = 'install';
            status({message: 'Устанавливаем обновление', version: update.version, progress: 100});
            await this.installer.install(downloaded, executable, startup);
            return true;
        } catch (error) {
            try {
                await mkdir(this.directory, {recursive: true});
                await writeFile(join(this.directory, 'last-error.json'), JSON.stringify({failedAt: new Date().toISOString(), stage, code: `UPDATE_${stage.toUpperCase()}_FAILED`}), 'utf8');
            } catch (recordingError) { console.error('Could not record application update failure:', recordingError instanceof Error ? recordingError.name : 'unknown error'); }
            console.error('Application update failed:', stage, error instanceof Error ? error.name : 'unknown error');
            status({message: stage === 'check' ? 'Не удалось проверить обновления. Запускаем приложение' : 'Не удалось обновить. Запускаем установленную версию', version});
            return false;
        }
    }
}
