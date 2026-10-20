const {test} = require('node:test');
const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const {mkdtemp, readFile, readdir, rm} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {ReleaseClient} = require('../dist/release-client.js');

const binary = Buffer.alloc(1_048_576, 0x21);
binary.write('MZ');
const checksum = createHash('sha256').update(binary).digest('hex');
const executable = {name: 'Boltunya.exe', size: binary.length, url: 'https://api.github.com/assets/1', browser_download_url: 'https://github.com/kreeeeeel/boltunya/releases/download/v2.6.18/Boltunya.exe'};
const hashes = {name: 'SHA256.txt', size: 100, url: 'https://api.github.com/assets/2', browser_download_url: 'https://github.com/kreeeeeel/boltunya/releases/download/v2.6.18/SHA256.txt'};
const release = {tag_name: 'v2.6.18', draft: false, prerelease: false, assets: [executable, hashes]};

test('checks a newer stable semantic version and skips equal, old, malformed and prerelease versions', async () => {
    for (const [version, prerelease, expected] of [['v2.6.18', false, true], ['v2.6.17', false, false], ['v2.5.999', false, false], ['v2.7.0', false, true], ['v3.0.0', false, true], ['v2.6.18-beta', false, false], ['v2.6.18', true, false]]) {
        const client = new ReleaseClient(async () => Response.json({...release, tag_name: version, prerelease}));
        assert.equal(Boolean(await client.latest('2.6.17')), expected, version);
    }
});

test('does not treat an unavailable or incomplete release as a successful update check', async () => {
    await assert.rejects(new ReleaseClient(async () => new Response('', {status: 404})).latest('2.6.17'), /HTTP 404/);
    await assert.rejects(new ReleaseClient(async () => Response.json({...release, assets: [hashes]})).latest('2.6.17'), /incomplete/);
});

test('downloads a verified Windows executable and reports bounded progress', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'boltunya-update-'));
    const progress = [];
    const client = new ReleaseClient(async url => String(url).endsWith('SHA256.txt') ? new Response(`\uFEFF${checksum}  Boltunya.exe\r\n`) : new Response(binary));
    try {
        const result = await client.download({version: '2.6.18', executable, checksum: hashes}, directory, value => progress.push(value));
        assert.equal(result.checksum, checksum);
        assert.equal(result.version, '2.6.18');
        assert.deepEqual(await readFile(result.path), binary);
        assert.equal(progress[0].percent, 0);
        assert.equal(progress.at(-1).percent, 100);
        assert.ok(progress.every(value => value.percent >= 0 && value.percent <= 100));
        assert.deepEqual(await readdir(directory), ['2.6.18.exe']);
    } finally { await rm(directory, {recursive: true, force: true}); }
});

test('rejects checksum mismatches, wrong file sizes and non-executables without leaving partial files', async () => {
    for (const scenario of ['checksum', 'size', 'header']) {
        const directory = await mkdtemp(join(tmpdir(), 'boltunya-update-'));
        const content = Buffer.from(binary);
        if (scenario === 'header') content.write('PK');
        const digest = scenario === 'checksum' ? '0'.repeat(64) : createHash('sha256').update(content).digest('hex');
        const client = new ReleaseClient(async url => String(url).endsWith('SHA256.txt') ? new Response(`${digest}  Boltunya.exe`) : new Response(content));
        try {
            const asset = scenario === 'size' ? {...executable, size: binary.length + 1} : executable;
            await assert.rejects(client.download({version: '2.6.18', executable: asset, checksum: hashes}, directory, () => {}), scenario === 'checksum' ? /checksum does not match/ : scenario === 'size' ? /incomplete/ : /Windows executable/);
            assert.deepEqual(await readdir(directory), []);
        } finally { await rm(directory, {recursive: true, force: true}); }
    }
});

test('refuses untrusted download redirects and never sends credentials', async () => {
    const client = new ReleaseClient(async (_url, options) => {
        assert.equal(options.headers.Authorization, undefined);
        assert.equal(options.redirect, 'manual');
        return new Response('', {status: 302, headers: {location: 'https://example.org/fake-release'}});
    });
    await assert.rejects(client.latest('2.6.17'), /not trusted/);
});
