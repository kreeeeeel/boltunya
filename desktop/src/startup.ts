import {app} from 'electron';
import {execFile} from 'node:child_process';

const RUN_KEY = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run';
const LEGACY_NAMES = ['Repli', 'Vela', 'Болтун'];

export class StartupRegistration {
    private enabled?: boolean;

    update(enabled: boolean): void {
        if (!app.isPackaged || process.env.BOLTUNYA_DATA_DIR || process.env.TIHO_DATA_DIR || this.enabled === enabled) return;
        app.setLoginItemSettings({name: 'Болтунья', openAtLogin: enabled, path: process.env.PORTABLE_EXECUTABLE_FILE ?? process.execPath, args: ['--startup']});
        this.enabled = enabled;
        LEGACY_NAMES.forEach(name => execFile('reg.exe', ['query', RUN_KEY, '/v', name], {windowsHide: true}, (error, output) => {
            if (error) return;
            // Only migrate registrations created by our former portable launcher.
            if (!/\\(?:Boltun|Repli|Vela)\\application\\|\\gpt-tg\\complie\\/iu.test(output)) return;
            execFile('reg.exe', ['delete', RUN_KEY, '/v', name, '/f'], {windowsHide: true}, deletion => {
                if (deletion) console.error('Could not migrate startup registration:', name);
            });
        }));
    }
}
