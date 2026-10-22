const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mkdtemp, readdir, readFile, rm, writeFile} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join} = require('node:path');
const {Backend} = require('../dist/backend.js');

async function failedStartup(directory) {
    const previousJavaHome = process.env.JAVA_HOME;
    try {
        process.env.JAVA_HOME = join(directory, 'missing-runtime');
        const backend = new Backend(false, directory, directory);
        await assert.rejects(backend.start(false), error => error.code === 'ENOENT' || error.message === 'Backend exited before startup');
    } finally {
        if (previousJavaHome === undefined) {
            delete process.env.JAVA_HOME;
        } else {
            process.env.JAVA_HOME = previousJavaHome;
        }
    }
}

test('reports a backend startup failure without creating a persistent log or journal', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-backend-no-log-'));
    try {
        await failedStartup(directory);
        assert.deepEqual(await readdir(directory), []);
    } finally { await rm(directory, {recursive: true, force: true}); }
});

test('leaves an existing legacy backend log unchanged after startup fails', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'aigram-backend-legacy-log-'));
    const log = join(directory, 'backend.log');
    try {
        await writeFile(log, 'Synthetic legacy log');
        await failedStartup(directory);
        assert.equal(await readFile(log, 'utf8'), 'Synthetic legacy log');
        assert.deepEqual(await readdir(directory), ['backend.log']);
    } finally { await rm(directory, {recursive: true, force: true}); }
});
