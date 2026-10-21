import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync} from 'node:fs';
import {join} from 'node:path';

export function legacyProfileActive(directory: string): boolean {
    const descriptor = join(directory, 'launch.url');
    if (existsSync(descriptor)) {
        const url = new URL(readFileSync(descriptor, 'utf8').trim());
        if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || !url.searchParams.get('key')) throw new Error('Не удалось проверить, закрыта ли предыдущая версия Aigram.');
        const probe = `const http=require('node:http');let value='';process.stdin.on('data',chunk=>value+=chunk);process.stdin.on('end',()=>{const url=new URL(JSON.parse(value));let connected=false;const request=http.get(url.origin+'/api/state',{headers:{Cookie:'tiho='+url.searchParams.get('key')},timeout:1200},response=>{response.resume();process.exit([200,401,403].includes(response.statusCode)?10:0)});request.on('socket',socket=>socket.once('connect',()=>connected=true));request.on('timeout',()=>{request.destroy();process.exit(connected?10:0)});request.on('error',()=>process.exit(connected?10:0));});`;
        try { execFileSync(process.execPath, ['-e', probe], {input: JSON.stringify(url.toString()), env: {...process.env, ELECTRON_RUN_AS_NODE: '1'}, windowsHide: true, stdio: ['pipe', 'ignore', 'ignore'], timeout: 2500}); }
        catch (error) { if ((error as {status?: number}).status === 10) return true; throw new Error('Не удалось проверить, закрыта ли предыдущая версия Aigram.'); }
    }
    if (process.platform !== 'win32') return false;
    const engine = join(directory, 'engine', 'ollama.exe');
    if (!existsSync(engine)) return false;
    const command = `$ErrorActionPreference='Stop';$expected=[Console]::In.ReadToEnd();$active=Get-CimInstance Win32_Process -Filter "Name='ollama.exe'" | Where-Object {$_.ExecutablePath -and [string]::Equals($_.ExecutablePath,$expected,[StringComparison]::OrdinalIgnoreCase)};if($active){exit 10}`;
    try { execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(command, 'utf16le').toString('base64')], {input: engine, windowsHide: true, stdio: ['pipe', 'ignore', 'ignore'], timeout: 8000, env: {...process.env, PSModulePath: undefined}}); }
    catch (error) { if ((error as {status?: number}).status === 10) return true; throw new Error('Не удалось проверить, закрыт ли встроенный Ollama. Внешний Ollama не изменён.'); }
    return false;
}
