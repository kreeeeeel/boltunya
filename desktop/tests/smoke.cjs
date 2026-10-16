const { _electron: electron } = require('../../web/node_modules/playwright');
const fs = require('node:fs/promises');
const path = require('node:path');
(async () => {
 const root=path.resolve(__dirname,'../..'), directory=path.join(root,'build','electron-smoke-'+Date.now());
 const application=await electron.launch({executablePath:process.env.BOLTUNYA_TEST_EXECUTABLE || path.join(root,'complie/win-unpacked/Болтунья.exe'),env:{...process.env,BOLTUNYA_DATA_DIR:directory,BOLTUNYA_OLLAMA_URL:'http://127.0.0.1:1'},timeout:60000});
 try {
  const page=await application.firstWindow();
  await page.getByRole('button',{name:'Подключить Telegram',exact:true}).waitFor({timeout:45000});
  const initial=await application.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().length);
  if(initial!==1)throw new Error('Unauthenticated application created a tray window');
  await page.getByRole('button',{name:'Развернуть',exact:true}).click();
  await page.waitForTimeout(400);
  const geometry=await application.evaluate(({BrowserWindow,screen})=>{const w=BrowserWindow.getAllWindows()[0];return {maximized:w.isMaximized(),fullscreen:w.isFullScreen(),bounds:w.getBounds(),work:screen.getDisplayMatching(w.getBounds()).workArea};});
  if(!geometry.maximized||geometry.fullscreen)throw new Error('Maximize changed to fullscreen');
  // Windows gives invisible frame margins to frameless maximized windows; the content area must fit the work area.
  const content=await application.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].getContentBounds());
  if(content.y+content.height>geometry.work.y+geometry.work.height+2)throw new Error('Maximize covers taskbar: '+JSON.stringify({content,work:geometry.work}));
  await page.getByRole('button',{name:'Восстановить размер',exact:true}).click();
  await page.screenshot({path:path.join(root,'docs/resources/electron-welcome.png')});
  // Overlay only the desktop's status reader. The isolated backend remains signed out and cannot send messages.
  await application.evaluate(()=>{const require=process.getBuiltinModule('module').createRequire(process.resourcesPath+'/app.asar/package.json'); const path=require('node:path'); const {Backend}=require(path.join(process.resourcesPath,'app.asar/dist/backend.js')); const original=Backend.prototype.request; Backend.prototype.request=async function(route,...args){const result=await original.call(this,route,...args);return route==='/api/state'?{...result,telegram:{...result.telegram,state:'ready'}}:result;};});
  await page.waitForTimeout(3000);
  const connected=await application.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().length);
  if(connected!==2)throw new Error('Authenticated tray menu missing');
  const tray=application.windows().find(w=>w.url().includes('/tray/'));
  if(!tray)throw new Error('Tray route missing');
  await tray.getByRole('menuitem',{name:'Открыть',exact:true}).waitFor();
  const traySize=await application.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().find(w=>w.webContents.getURL().includes('/tray/')).getSize());
  if(traySize[0]>230||traySize[1]>190)throw new Error('Tray is oversized');
  const traySpacing=await tray.evaluate(()=>{const root=document.querySelector('[data-qa="tray-menu"]').getBoundingClientRect(),items=[...document.querySelectorAll('[role="menuitem"]')],last=items.at(-1).getBoundingClientRect();return {bottom:innerHeight-last.bottom,rootHeight:root.height};});
  if(traySpacing.bottom>6||traySpacing.bottom<2)throw new Error('Tray bottom spacing is uneven: '+JSON.stringify(traySpacing));
  await page.getByRole('button',{name:'Закрыть окно',exact:true}).click();
  await page.waitForTimeout(500);
  const hidden=await application.evaluate(({BrowserWindow})=>!BrowserWindow.getAllWindows().find(w=>!w.webContents.getURL().includes('/tray/')).isVisible());
  if(!hidden)throw new Error('Close did not hide to tray');
  await application.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().find(w=>w.webContents.getURL().includes('/tray/')).show());
  await tray.getByRole('menuitem',{name:'Открыть',exact:true}).click();
  await page.waitForTimeout(250);
  console.log(JSON.stringify({unauthenticatedWindows:initial,maximized:geometry.maximized,taskbarPreserved:true,traySize,closeToTray:hidden}));
 } finally {await application.close();}
 await fs.access(path.join(directory,'launch.url')).then(()=>{throw new Error('Backend session descriptor survived shutdown');},()=>{});
 const startupDirectory=directory+'-startup';
 await fs.mkdir(startupDirectory,{recursive:true});
 await fs.writeFile(path.join(startupDirectory,'settings.json'),JSON.stringify({resumeOnStartup:true}));
 const startupApp=await electron.launch({executablePath:process.env.BOLTUNYA_TEST_EXECUTABLE || path.join(root,'complie/win-unpacked/Болтунья.exe'),args:['--startup'],env:{...process.env,BOLTUNYA_DATA_DIR:startupDirectory,BOLTUNYA_OLLAMA_URL:'http://127.0.0.1:1'},timeout:60000});
 try {
  const startupPage=await startupApp.firstWindow();
  await startupPage.getByRole('button',{name:'Подключить Telegram',exact:true}).waitFor({timeout:45000});
  const startupTray=startupApp.windows().find(page=>page!==startupPage) ?? await startupApp.waitForEvent('window',{timeout:15000});
  if(!startupTray)throw new Error('Windows startup has no tray menu');
  await startupTray.waitForURL('**/tray/');
  await startupTray.getByRole('menuitem',{name:'Открыть',exact:true}).waitFor();
  await startupPage.waitForTimeout(1800);
  const startupState=await startupApp.evaluate(({BrowserWindow})=>({hidden:BrowserWindow.getAllWindows().every(window=>!window.isVisible()),windows:BrowserWindow.getAllWindows().length}));
  if(!startupState.hidden||startupState.windows!==2)throw new Error('Windows startup showed a window');
  const assistantEnabled=await startupPage.evaluate(async()=>!(await(await fetch('/api/state')).json()).settings.paused);
  if(!assistantEnabled)throw new Error('Automatic assistant startup was ignored');
  await startupApp.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().find(window=>window.webContents.getURL().includes('/tray/')).show());
  await startupTray.getByRole('menuitem',{name:'Открыть',exact:true}).click();
  const opened=await startupApp.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows().find(window=>!window.webContents.getURL().includes('/tray/')).isVisible());
  if(!opened)throw new Error('Tray did not open the startup window');
  console.log(JSON.stringify({windowsStartupTrayOnly:startupState.hidden,assistantEnabled,opensFromTray:opened}));
 } finally {await startupApp.close();}
 await fs.access(path.join(startupDirectory,'launch.url')).then(()=>{throw new Error('Startup backend survived shutdown');},()=>{});
})().catch(error=>{console.error(error);process.exitCode=1;});


