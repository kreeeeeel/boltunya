import {app} from 'electron';
import {execFile} from 'node:child_process';
import {APP_NAME} from './brand';
import {ownsLegacyStartup} from './startup-policy';

const RUN_KEY = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run';
const LEGACY_NAMES = ['Болтунья', 'Boltunya', 'Repli', 'Vela', 'Болтун'];

export class StartupRegistration {
    private enabled?: boolean;

    update(enabled: boolean): void {
        if (!app.isPackaged || process.env.AIGRAM_DATA_DIR || process.env.BOLTUNYA_DATA_DIR || process.env.TIHO_DATA_DIR || this.enabled === enabled) return;
        const executable = process.env.PORTABLE_EXECUTABLE_FILE ?? process.execPath;
        app.setLoginItemSettings({name: APP_NAME, openAtLogin: enabled, path: executable, args: ['--startup']});
        this.enabled = enabled;
        LEGACY_NAMES.forEach(name => execFile('reg.exe', ['query', RUN_KEY, '/v', name], {windowsHide: true}, (error, output) => {
            if (error) return;
            if (!ownsLegacyStartup(output, executable)) return;
            execFile('reg.exe', ['delete', RUN_KEY, '/v', name, '/f'], {windowsHide: true}, deletion => {
                if (deletion) console.error('Could not migrate startup registration:', name);
            });
        }));
    }
}
