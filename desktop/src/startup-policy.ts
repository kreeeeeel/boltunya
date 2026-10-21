import {win32} from 'node:path';

const LEGACY_EXECUTABLES = new Set(['boltunya.exe', 'болтунья.exe', 'болтун.exe']);

export function ownsLegacyStartup(output: string, currentExecutable: string): boolean {
    const marker = ['REG_EXPAND_SZ', 'REG_SZ'].find(type => output.includes(type));
    if (!marker) return false;
    const markerAt = output.indexOf(marker);
    if (markerAt > 0 && output[markerAt - 1].trim() !== '' || output[markerAt + marker.length]?.trim() !== '') return false;
    const command = output.slice(markerAt + marker.length).trim();
    if (!command) return false;
    const end = command.startsWith('"') ? command.indexOf('"', 1) : Array.from(command).findIndex(char => char.trim() === '');
    if (command.startsWith('"') && end < 0) return false;
    const executable = command.slice(command.startsWith('"') ? 1 : 0, end < 0 ? undefined : end).replaceAll('/', '\\');
    if (!executable) return false;
    if (executable.toLowerCase() === currentExecutable.replaceAll('/', '\\').toLowerCase()) return true;
    const normalized = executable.toLowerCase();
    return LEGACY_EXECUTABLES.has(win32.basename(normalized)) || ['\\boltun\\application\\', '\\repli\\application\\', '\\vela\\application\\', '\\gpt-tg\\complie\\'].some(directory => normalized.includes(directory));
}
