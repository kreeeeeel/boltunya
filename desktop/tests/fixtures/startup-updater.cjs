const {app} = require('electron');
const {join} = require('node:path');
const {createServer} = require('node:http');
const root = process.env.BOLTUNYA_UPDATER_ROOT;
Object.defineProperty(app, 'isPackaged', {value: true});
Object.defineProperty(process, 'resourcesPath', {value: join(root, 'desktop/assets')});
const {Backend} = require(join(root, 'desktop/dist/backend.js'));
const {ReleaseClient} = require(join(root, 'desktop/dist/release-client.js'));
const state = {telegram: {state: 'waitPhoneNumber'}, settings: {paused: true, closeToTray: true, launchAtLogin: false}};
const server = createServer((request, response) => {
    response.setHeader('Content-Type', 'text/html;charset=utf-8');
    if (request.url === '/') response.setHeader('Set-Cookie', 'tiho=synthetic-desktop-session;Path=/;HttpOnly;SameSite=Strict');
    response.end(request.url === '/tray/' ? '<div data-qa="tray-menu" style="height:146px">Synthetic tray</div>' : '<main data-qa="startup-main">Synthetic startup</main>');
});
ReleaseClient.prototype.latest = async () => new Promise(resolve => { globalThis.__finishUpdateCheck = () => resolve(undefined); });
ReleaseClient.prototype.download = async () => { throw new Error('This test must not download releases'); };
Backend.prototype.start = async startup => {
    if (!startup) throw new Error('Startup flag was lost');
    globalThis.__startupBackendStarted = true;
    return `http://127.0.0.1:${server.address().port}/`;
};
Backend.prototype.request = async () => state;
Backend.prototype.onExit = () => {};
Backend.prototype.close = async () => { server.close(); };
server.listen(0, '127.0.0.1', () => require(join(root, 'desktop/dist/main.js')));
