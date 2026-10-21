const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mkdtemp, realpath, rm, writeFile} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {StorageLocations} = require('../dist/storage-locations.js');

test('opens only a fresh allowlisted directory from the authenticated backend', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'boltunya-storage-'));
    const opened = [];
    let requests = 0;
    const locations = new StorageLocations(async () => { requests++; return {locations: [{id: 'memory', path: directory, kind: 'directory', openable: true}]}; }, async path => { opened.push(path); return ''; });
    try {
        await locations.openLocation('memory');
        await locations.openLocation('memory');
        assert.deepEqual(opened, [await realpath(directory), await realpath(directory)]);
        assert.equal(requests, 2);
        await assert.rejects(locations.openLocation('../../Windows'), error => error.message.includes('Unknown storage'));
        await assert.rejects(locations.openLocation({id: 'memory'}), error => error.message.includes('Unknown storage'));
        assert.equal(requests, 2);
    } finally { await rm(directory, {recursive: true, force: true}); }
});

test('rejects external, unavailable, relative and file paths', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'boltunya-storage-'));
    const file = join(directory, 'data.json');
    await writeFile(file, '{}');
    try {
        for (const entry of [
            {path: directory, kind: 'external', openable: true},
            {path: directory, kind: 'directory', openable: false},
            {path: '../memory', kind: 'directory', openable: true},
            {path: file, kind: 'directory', openable: true},
        ]) {
            const locations = new StorageLocations(async () => ({locations: [{id: 'memory', ...entry}]}), async () => { throw new Error('Must not open'); });
            await assert.rejects(locations.openLocation('memory'), error => ['unavailable', 'not a directory'].some(text => error.message.includes(text)));
        }
    } finally { await rm(directory, {recursive: true, force: true}); }
});
