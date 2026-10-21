const {app} = require('electron');
const {join} = require('node:path');
const root = process.env.BOLTUNYA_UPDATER_ROOT;
const {UpdateWindow} = require(join(root, 'desktop/dist/update-window.js'));
app.whenReady().then(async () => {
    const window = new UpdateWindow();
    await window.open(join(root, 'desktop/dist/updater-preload.js'), join(root, 'desktop/assets/aigram.png'), '2.6.17');
    window.update({message: 'Загружаем обновления', version: '2.6.18', progress: 42});
}).catch(error => {console.error('Updater smoke failed:', error); app.exit(1);});
app.on('window-all-closed', () => app.quit());
