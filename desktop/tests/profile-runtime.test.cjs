const {test} = require('node:test');
const assert = require('node:assert/strict');
const {execFile,spawn} = require('node:child_process');
const {promisify} = require('node:util');
const {mkdtemp,mkdir,writeFile,copyFile,readFile,rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {legacyProfileActive} = require('../dist/profile-activity.js');
const {DesktopProfile} = require('../dist/profile.js');
const execute=promisify(execFile),quote=value=>`'${value.replaceAll("'","''")}'`;
test('only an application-owned legacy runtime blocks migration and an external runtime is left running',{skip:process.platform!=='win32',timeout:30000},async()=>{
    const directory=await mkdtemp(join(tmpdir(),'aigram-runtime-migration-')),local=join(directory,'Local'),source=join(local,'Tiho'),owned=join(source,'engine/ollama.exe'),external=join(directory,'external/ollama.exe');
    await mkdir(join(owned,'..'),{recursive:true});await mkdir(join(external,'..'),{recursive:true});
    const script=join(directory,'compile.ps1');
    await writeFile(script,`\uFEFF$ErrorActionPreference='Stop'\nAdd-Type -TypeDefinition 'public class ProfileRuntime {public static void Main(){System.Threading.Thread.Sleep(25000);}}' -OutputAssembly ${quote(owned)} -OutputType WindowsApplication`);
    await execute('powershell.exe',['-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',script],{windowsHide:true,timeout:15000,env:{...process.env,PSModulePath:undefined}});
    await copyFile(owned,external);
    let processHandle;
    const start=async path=>{processHandle=spawn(path,[],{windowsHide:true,stdio:'ignore'});await new Promise((resolve,reject)=>{processHandle.once('spawn',resolve);processHandle.once('error',reject);});};
    const stop=async()=>{if(processHandle){const ended=new Promise(resolve=>processHandle.once('exit',resolve));processHandle.kill();await ended;processHandle=undefined;}};
    try{
        await start(owned);assert.equal(legacyProfileActive(source),true);await stop();
        await start(external);assert.equal(legacyProfileActive(source),false);
        new DesktopProfile({LOCALAPPDATA:local},join(directory,'Roaming')).prepare();
        assert.equal(processHandle.exitCode,null);
        assert.deepEqual(await readFile(join(local,'Aigram/engine/ollama.exe')),await readFile(owned));
    }finally{await stop();await rm(directory,{recursive:true,force:true});}
});
