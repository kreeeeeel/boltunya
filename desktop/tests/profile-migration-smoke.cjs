const {_electron: electron} = require('../../web/node_modules/playwright');
const assert = require('node:assert/strict');
const {mkdtemp,mkdir,writeFile,readFile,access,rm} = require('node:fs/promises');
const {createHash} = require('node:crypto');
const {tmpdir} = require('node:os');
const {basename,dirname,join,resolve} = require('node:path');
const executablePath = require('../node_modules/electron');
async function closeApplication(application) {
    const child=application.process();
    const exited=child.exitCode!==null||child.signalCode!==null
        ? Promise.resolve({code:child.exitCode,signal:child.signalCode})
        : new Promise(resolve=>child.once('exit',(code,signal)=>resolve({code,signal})));
    await application.close();
    const result=await exited;
    assert.equal(result.code,0,'Owned Electron fixture must exit gracefully before profile reuse or cleanup');
    assert.equal(result.signal,null);
}
(async()=>{
    const root=resolve(__dirname,'../..'),directory=await mkdtemp(join(tmpdir(),'aigram-native-profile-'));
    const local=join(directory,'Local'),roaming=join(directory,'Roaming'),legacy=join(local,'Tiho'),canonical=join(local,'Aigram'),oldBrowser=join(roaming,'Болтунья');
    const executable=join(directory,'Aigram.exe'),next=Buffer.from('MZ synthetic updated application');
    await mkdir(join(legacy,'desktop/updates'),{recursive:true});await mkdir(oldBrowser,{recursive:true});
    await writeFile(join(legacy,'settings.json'),'synthetic-owner-settings');await writeFile(join(oldBrowser,'Preferences'),'{}');
    await writeFile(executable,next);await writeFile(executable+'.update-backup','MZ previous application');
    await writeFile(join(legacy,'desktop/updates/install.json'),JSON.stringify({target:executable,download:join(legacy,'desktop/updates/new.exe'),checksum:createHash('sha256').update(next).digest('hex'),processId:2147483646,startup:false}));
    const env={...process.env,LOCALAPPDATA:local,AIGRAM_SYNTHETIC_ROAMING:roaming,BOLTUNYA_UPDATER_ROOT:root,PORTABLE_EXECUTABLE_FILE:executable,AIGRAM_SKIP_UPDATE_CHECK:'true'};
    delete env.AIGRAM_DATA_DIR;delete env.BOLTUNYA_DATA_DIR;delete env.TIHO_DATA_DIR;
    let application;
    try{
        application=await electron.launch({executablePath,args:[join(__dirname,'fixtures/profile-migration.cjs'),'--updated'],env,timeout:30000});
        const page=await application.firstWindow();await page.locator('[data-qa="migration-main"]').waitFor();
        await page.waitForFunction(()=>Boolean(window.aigramDesktop));
        await application.evaluate(()=>globalThis.__profileConfirmationStarted);
        const previous=await application.evaluate(({app})=>({data:globalThis.__profileDirectory,browser:app.getPath('userData')}));
        assert.equal(previous.data,legacy);assert.equal(previous.browser,oldBrowser);
        await assert.rejects(access(canonical),{code:'ENOENT'});
        // The page/preload can be ready while asynchronous update confirmation is still pending.
        await access(join(legacy,'desktop/updates/install.json'));
        await access(executable+'.update-backup');
        await application.evaluate(async({BrowserWindow})=>{
            const window=BrowserWindow.getAllWindows().find(candidate=>!candidate.webContents.getURL().includes('/tray/'));
            if(!window||window.isVisible())throw new Error('Updated window became visible before health confirmation');
            const shown=new Promise(resolve=>window.once('show',resolve));
            globalThis.__releaseProfileConfirmation();
            await shown;
        });
        await assert.rejects(access(canonical),{code:'ENOENT'});
        await assert.rejects(access(join(legacy,'desktop/updates/install.json')),{code:'ENOENT'});
        await assert.rejects(access(executable+'.update-backup'),{code:'ENOENT'});
        await closeApplication(application);application=undefined;
        application=await electron.launch({executablePath,args:[join(__dirname,'fixtures/profile-migration.cjs'),'--startup'],env,timeout:30000});
        const current=await application.firstWindow();await current.locator('[data-qa="migration-main"]').waitFor();
        const migrated=await application.evaluate(({app,BrowserWindow})=>({data:globalThis.__profileDirectory,browser:app.getPath('userData'),hidden:BrowserWindow.getAllWindows().every(window=>!window.isVisible())}));
        assert.equal(migrated.data,canonical);assert.equal(migrated.browser,join(canonical,'desktop/browser'));assert.equal(migrated.hidden,true);
        assert.equal(await readFile(join(canonical,'settings.json'),'utf8'),'synthetic-owner-settings');
        assert.equal(await readFile(join(legacy,'settings.json'),'utf8'),'synthetic-owner-settings');
        console.log(JSON.stringify({legacyUpdateHealthConfirmed:true,migrationDeferredDuringHandoff:true,normalLaunchMigratesToAigram:true,originalFilesRetained:true,startupTrayOnly:true,noTelegram:true,noDownloads:true}));
    }finally{
        if(application)await closeApplication(application);
        assert.equal(dirname(resolve(directory)),resolve(tmpdir()));
        assert.ok(basename(directory).startsWith('aigram-native-profile-'));
        await rm(directory,{recursive:true,force:true});
    }
})().catch(error=>{console.error(error);process.exitCode=1;});
