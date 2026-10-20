const {test} = require('node:test');
const assert = require('node:assert/strict');
const {createServer} = require('node:http');
const {spawn} = require('node:child_process');
const {mkdtemp, readFile, writeFile, unlink, rmdir} = require('node:fs/promises');
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
    const descriptor = join(directory, 'launch.url');
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
        await unlink(descriptor);
        await unlink(output).catch(error => {if (error.code !== 'ENOENT') throw error;});
        await rmdir(directory);
    });
    const base = `http://127.0.0.1:${server.address().port}`;
    await writeFile(descriptor, options.descriptor ?? `${base}/?key=${secret}`);
    const args = ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', script, '-DataDirectory', directory, '-OutputPath', output];
    if (options.testModel) args.push('-TestModel');
    await new Promise((resolve, reject) => {
        const child = spawn(powershell, args, {windowsHide: true, env: {...process.env, BOLTUNYA_OLLAMA_URL: base}});
        let errors = '';
        child.stderr.on('data', chunk => {errors += chunk;});
        child.once('error', reject);
        child.once('exit', code => code === 0 ? resolve() : reject(new Error(`Diagnostics exited ${code}: ${errors}`)));
    });
    const raw = await readFile(output, 'utf8');
    assert.ok(!raw.includes(secret), 'Report must exclude credentials, identifiers, prompts, messages and generated text');
    return {report: JSON.parse(raw), requests};
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
    assert.deepEqual(JSON.parse(generation.body), {text: 'Say hello in one short sentence.', rule: {model: 'gemma3:4b', useDefaultModel: false, internetAccess: false, matchMyStyle: false, useMemory: false}});
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
});

test('rejects a nonlocal application descriptor before sending its cookie', async t => {
    const {report, requests} = await collect(t, {descriptor: `https://example.invalid/?key=${secret}`, testModel: true});
    assert.equal(report.application.reachable, false);
    assert.equal(report.application.status, 'INVALID_DESCRIPTOR');
    assert.equal(report.generation.status, 'APPLICATION_UNREACHABLE');
    assert.ok(requests.every(request => ['/api/version', '/api/tags'].includes(request.path)));
});
