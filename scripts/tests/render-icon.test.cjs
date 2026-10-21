const {test} = require('node:test');
const assert = require('node:assert/strict');
const {execFile} = require('node:child_process');
const {mkdtemp, readFile, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');
const {promisify} = require('node:util');
const sharp = require('../../web/node_modules/sharp');

test('renders the Aigram vector into a complete Windows icon and high resolution PNG', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-icon-'));
    try {
        const ico = join(directory, 'aigram.ico'), png = join(directory, 'aigram.png');
        await promisify(execFile)(process.execPath, [resolve(__dirname, '../render-icon.cjs'), ico, png], {windowsHide: true});
        const icon = await readFile(ico);
        assert.equal(icon.readUInt16LE(0), 0);
        assert.equal(icon.readUInt16LE(2), 1);
        assert.equal(icon.readUInt16LE(4), 7);
        const sizes = [16, 24, 32, 48, 64, 128, 256];
        await Promise.all(sizes.map(async (size, index) => {
            const entry = 6 + index * 16;
            const length = icon.readUInt32LE(entry + 8), offset = icon.readUInt32LE(entry + 12);
            const frame = await sharp(icon.subarray(offset, offset + length)).metadata();
            assert.equal(frame.width, size);
            assert.equal(frame.height, size);
            assert.equal(frame.format, 'png');
        }));
        const metadata = await sharp(png).metadata();
        assert.equal(metadata.width, 512);
        assert.equal(metadata.height, 512);
    } finally { await rm(directory, {recursive: true, force: true}); }
});
