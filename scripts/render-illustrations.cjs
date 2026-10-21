const sharp = require('../web/node_modules/sharp');
const {join} = require('node:path');

Promise.all(['welcome', 'conversations', 'activity'].map(name => {
    const root = join(__dirname, '../web/public/assets/illustrations');
    return sharp(join(root, `${name}.svg`), {density: 192}).resize(1254, 1254).png().toFile(join(root, `${name}.png`));
})).catch(error => {console.error(error.message); process.exitCode = 1;});
