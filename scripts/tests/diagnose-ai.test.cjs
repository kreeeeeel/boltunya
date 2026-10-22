const {test} = require('node:test');
const assert = require('node:assert/strict');
const {createServer} = require('node:http');
const {spawn} = require('node:child_process');
const {mkdtemp, mkdir, readFile, writeFile, rm, access} = require('node:fs/promises');
const {tmpdir} = require('node:os');
const {join, resolve} = require('node:path');

const secret = 'PRIVATE_CHAT_AND_CREDENTIAL_SENTINEL';
const script = resolve(__dirname, '../diagnose-ai.ps1');
const powershell = join(process.env.SystemRoot, 'System32/WindowsPowerShell/v1.0/powershell.exe');

function state() {
    return {
        telegram: {state: 'ready', name: secret, userId: secret, error: secret},
        engine: {online: true, installed: true, error: secret, models: [{id: 'gemma3:4b', installed: true, memoryGb: 8, supportsAutomation: true}]},
        settings: {paused: false, defaultModel: 'gemma3:4b', globalPrompt: secret, groupReplies: 'disabled', ignoreAssistantTasks: true, rules: {[secret]: {enabled: true, instructions: secret, useDefaultModel: true}}},
        chats: [{id: secret, title: secret, preview: secret}],
        activity: [{id: secret, chatId: secret, title: secret, detail: secret, preview: secret, thinking: secret, status: 'error', phase: 'reply', durationMs: 42, attempts: 1, inputTokens: 10, outputTokens: 5}],
        replyQueue: {paused: false, queued: 0, running: 0},
    };
}

async function collect(t, options = {}) {
    const directory = await mkdtemp(join(tmpdir(), 'boltunya-diagnostic-test-'));
    const output = join(directory, 'report.json');
    const profileDirectory = options.defaultProfile ? join(directory, options.defaultProfile) : directory;
    await mkdir(profileDirectory, {recursive: true});
    const descriptor = join(profileDirectory, 'launch.url');
    const requests = [];
    const server = createServer((request, response) => {
        let body = '';
        request.on('data', chunk => {body += chunk;});
        request.on('end', () => {
            requests.push({path: request.url, cookie: request.headers.cookie, body});
            response.setHeader('Content-Type', 'application/json');
            if (!['/api/version', '/api/tags'].includes(request.url) && request.headers.cookie !== `tiho=${secret}`) {
                response.statusCode = 401;
                response.end('{"code":"UNAUTHORIZED"}');
                return;
            }
            if (request.url === '/api/state') response.end(JSON.stringify(options.state ?? state()));
            else if (request.url === '/api/version') response.end('{"version":"0.40.4"}');
            else if (request.url === '/api/tags') response.end('{"models":[{"name":"gemma3:4b"},{"model":"custom:latest"}]}');
            else if (request.url === '/api/preview') {
                response.statusCode = options.error ? 502 : 200;
                response.end(JSON.stringify(options.error ? {code: 'ENGINE_UNAVAILABLE', message: secret} : {text: secret, durationMs: 123, tokensPerSecond: 18.5}));
            } else {response.statusCode = 404; response.end('{}');}
        });
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    t.after(async () => {
        await new Promise(resolve => server.close(resolve));
        await rm(directory, {recursive: true, force: true});
    });
    const base = `http://127.0.0.1:${server.address().port}`;
    const originalDescriptor = options.descriptor ?? `${base}/?key=${secret}`;
    await writeFile(descriptor, originalDescriptor);
    if (options.decoyLegacy) {
        await mkdir(join(directory, 'Tiho'), {recursive: true});
        await writeFile(join(directory, 'Tiho/launch.url'), options.decoyLegacy === 'valid' ? `${base}/?key=${secret}` : 'https://example.invalid/?key=not-selected');
    }
    const args = ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script, '-OutputPath', output];
    if (options.directoryArgument !== false) args.push('-DataDirectory', profileDirectory);
    if (options.testModel) args.push('-TestModel');
    await new Promise((resolve, reject) => {
        const child = spawn(powershell, args, {windowsHide: true, env: {...process.env, AIGRAM_DATA_DIR: undefined, BOLTUNYA_DATA_DIR: undefined, TIHO_DATA_DIR: undefined, LOCALAPPDATA: directory, BOLTUNYA_OLLAMA_URL: base, ...options.environment?.({directory, profileDirectory})}});
        let errors = '';
        child.stderr.on('data', chunk => {errors += chunk;});
        child.once('error', reject);
        child.once('exit', code => code === 0 ? resolve() : reject(new Error(`Diagnostics exited ${code}: ${errors}`)));
    });
    const raw = await readFile(output, 'utf8');
    assert.ok(!raw.includes(secret), 'Report must exclude credentials, identifiers, prompts, messages and generated text');
    assert.equal(await readFile(descriptor, 'utf8'), originalDescriptor, 'Diagnostics must never change the descriptor or migrate data');
    return {report: JSON.parse(raw), requests, directory};
}

test('collects allowlisted runtime metadata and runs only an explicit synthetic generation', async t => {
    const {report, requests} = await collect(t, {testModel: true});
    assert.equal(report.application.reachable, true);
    assert.equal(report.application.enabledChats, 1);
    assert.deepEqual(report.ollama.models, ['gemma3:4b']);
    assert.equal(report.ollama.version, '0.40.4');
    assert.deepEqual(report.ollama.serverModels, ['custom:latest', 'gemma3:4b']);
    assert.equal(report.generation.status, 'OK');
    assert.equal(report.generation.durationMs, 123);
    assert.equal(report.generation.tokensPerSecond, 18.5);
    const generation = requests.find(request => request.path === '/api/preview');
    assert.equal(generation.cookie, `tiho=${secret}`);
    assert.deepEqual(JSON.parse(generation.body), {text: 'Say hello in one short sentence.', rule: {model: 'gemma3:4b', useDefaultModel: false, matchMyStyle: false, useMemory: false}});
    assert.equal(requests.find(request => request.path === '/api/version').cookie, undefined);
    assert.equal(requests.find(request => request.path === '/api/tags').cookie, undefined);
});

test('reports disabled automation and missing models without running generation by default', async t => {
    const fixture = state();
    fixture.settings.paused = true;
    fixture.settings.rules = {};
    fixture.replyQueue.paused = true;
    fixture.engine.online = false;
    fixture.engine.models[0].installed = false;
    const {report, requests} = await collect(t, {state: fixture});
    assert.equal(report.generation.status, 'NOT_REQUESTED');
    assert.ok(!requests.some(request => request.path === '/api/preview'));
    assert.ok(['AI_PAUSED', 'QUEUE_PAUSED', 'NO_ENABLED_CHATS', 'OLLAMA_OFFLINE', 'DEFAULT_MODEL_NOT_INSTALLED'].every(code => report.findings.includes(code)));
});

test('keeps a structured generation error without copying the server response', async t => {
    const {report} = await collect(t, {testModel: true, error: true});
    assert.equal(report.generation.status, 'ENGINE_UNAVAILABLE');
    assert.equal(report.generation.httpStatus, 502);
    assert.ok(report.generation.requestDurationMs >= 0);
});

test('rejects a nonlocal application descriptor before sending its cookie', async t => {
    const {report, requests} = await collect(t, {descriptor: `https://example.invalid/?key=${secret}`, testModel: true});
    assert.equal(report.application.reachable, false);
    assert.equal(report.application.status, 'INVALID_DESCRIPTOR');
    assert.equal(report.generation.status, 'APPLICATION_UNREACHABLE');
    assert.ok(requests.every(request => ['/api/version', '/api/tags'].includes(request.path)));
});

test('reads the deferred Tiho profile only when the default Aigram launch descriptor is absent', async t => {
    const {report, directory} = await collect(t, {defaultProfile: 'Tiho', directoryArgument: false});
    assert.equal(report.application.reachable, true);
    await assert.rejects(access(join(directory, 'Aigram')));
});

test('prefers the canonical Aigram descriptor when both default profiles exist', async t => {
    const {report} = await collect(t, {defaultProfile: 'Aigram', directoryArgument: false, decoyLegacy: true});
    assert.equal(report.application.reachable, true);
});

test('does not switch profiles when the canonical descriptor exists but is invalid', async t => {
    const {report} = await collect(t, {defaultProfile: 'Aigram', directoryArgument: false, decoyLegacy: 'valid', descriptor: 'https://example.invalid/?key=not-local'});
    assert.equal(report.application.reachable, false);
    assert.equal(report.application.status, 'INVALID_DESCRIPTOR');
});

test('an explicit directory takes precedence over environment overrides and the legacy fallback', async t => {
    const {report} = await collect(t, {decoyLegacy: true, environment: ({directory}) => ({AIGRAM_DATA_DIR: join(directory, 'missing-explicit-environment')})});
    assert.equal(report.application.reachable, true);
});

test('explicit environment overrides retain their priority without falling back to Tiho', async t => {
    for (const selected of ['AIGRAM_DATA_DIR', 'BOLTUNYA_DATA_DIR', 'TIHO_DATA_DIR']) {
        const {report} = await collect(t, {
            directoryArgument: false,
            decoyLegacy: true,
            environment: ({directory, profileDirectory}) => ({
                [selected]: profileDirectory,
                ...(selected === 'AIGRAM_DATA_DIR' ? {BOLTUNYA_DATA_DIR: join(directory, 'wrong-boltunya'), TIHO_DATA_DIR: join(directory, 'wrong-tiho')} : selected === 'BOLTUNYA_DATA_DIR' ? {TIHO_DATA_DIR: join(directory, 'wrong-tiho')} : {}),
            }),
        });
        assert.equal(report.application.reachable, true, selected);
    }
});
