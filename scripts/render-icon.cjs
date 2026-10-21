const sharp = require('../web/node_modules/sharp');
const {mkdir, readFile, writeFile} = require('node:fs/promises');
const {dirname, join} = require('node:path');

const sizes = [16, 24, 32, 48, 64, 128, 256];

(async () => {
    const [output, png] = process.argv.slice(2);
    if (!output || !png) throw new Error('Usage: node render-icon.cjs icon.ico icon.png');
    const svg = await readFile(join(__dirname, '../web/public/assets/brand/aigram.svg'));
    const images = await Promise.all(sizes.map(size => sharp(svg, {density: 384}).resize(size, size).png().toBuffer()));
    const header = Buffer.alloc(6 + sizes.length * 16);
    header.writeUInt16LE(1, 2);
    header.writeUInt16LE(sizes.length, 4);
    let offset = header.length;
    sizes.forEach((size, index) => {
        const entry = 6 + index * 16;
        header.writeUInt8(size === 256 ? 0 : size, entry);
        header.writeUInt8(size === 256 ? 0 : size, entry + 1);
        header.writeUInt16LE(1, entry + 4);
        header.writeUInt16LE(32, entry + 6);
        header.writeUInt32LE(images[index].length, entry + 8);
        header.writeUInt32LE(offset, entry + 12);
        offset += images[index].length;
    });
    await Promise.all([mkdir(dirname(output), {recursive: true}), mkdir(dirname(png), {recursive: true})]);
    await writeFile(output, Buffer.concat([header, ...images]));
    await sharp(svg, {density: 384}).resize(512, 512).png().toFile(png);
})().catch(error => {console.error(error.message); process.exitCode = 1;});
