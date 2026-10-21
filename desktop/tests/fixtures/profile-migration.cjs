const {app} = require('electron');
const {createServer} = require('node:http');
const {join} = require('node:path');
const root = process.env.BOLTUNYA_UPDATER_ROOT;
app.setPath('appData', process.env.AIGRAM_SYNTHETIC_ROAMING);
Object.defineProperty(app, 'isPackaged', {value: true});
Object.defineProperty(process, 'resourcesPath', {value: join(root, 'desktop/assets')});
const {Backend} = require(join(root, 'desktop/dist/backend.js'));
if (process.argv.includes('--updated')) {
    const {PortableUpdate} = require(join(root, 'desktop/dist/portable-update.js'));
    const confirm = PortableUpdate.prototype.confirm;
    let started;
    globalThis.__profileConfirmationStarted = new Promise(resolve => {started = resolve;});
    let release;
    const released = new Promise(resolve => {release = resolve;});
    globalThis.__releaseProfileConfirmation = () => release();
    PortableUpdate.prototype.confirm = async function(...argumentsList) {
        started();
        await released;
        return confirm.apply(this, argumentsList);
    };
}
const state = {telegram:{state:'waitPhoneNumber'},settings:{paused:true,closeToTray:true,launchAtLogin:false}};
const server = createServer((request,response)=>{response.setHeader('Content-Type','text/html');response.end(request.url.startsWith('/tray/')?'<div data-qa="tray-menu" style="height:146px">Synthetic tray</div>':'<main data-qa="migration-main">Synthetic profile</main>');});
Backend.prototype.start = async function() {globalThis.__profileDirectory=this.directory;return `http://127.0.0.1:${server.address().port}/`;};
Backend.prototype.request = async()=>state;
Backend.prototype.onExit = ()=>{};
Backend.prototype.close = async()=>{
    server.closeAllConnections();
    await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));
};
server.listen(0,'127.0.0.1',()=>require(join(root,'desktop/dist/main.js')));
