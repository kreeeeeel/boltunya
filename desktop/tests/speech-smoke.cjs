const {_electron: electron} = require('../../web/node_modules/playwright');
const assert = require('node:assert/strict');
const {execFile} = require('node:child_process');
const {createHash} = require('node:crypto');
const {createReadStream} = require('node:fs');
const fs = require('node:fs/promises');
const http = require('node:http');
const path = require('node:path');
const {promisify} = require('node:util');
const ru = require('../../web/src/shared/i18n/ru.json');

const execute = promisify(execFile);
const VOICE_ID = 'piper-ru-irina';
const SAMPLE = 'Привет! Это проверка настоящего голоса в приложении. Сегодня хорошая погода.';
const INFERENCE_TIMEOUT_MS = 150_000;
const TERMINAL_STATUSES = new Set(['ready', 'error', 'cancelled']);
const sleep = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

async function fingerprint(file) {
    const hash = createHash('sha256');
    for await (const chunk of createReadStream(file)) hash.update(chunk);
    return hash.digest('hex');
}

function inside(parent, child) {
    const relative = path.relative(parent, child);
    assert.ok(relative && !relative.startsWith('..') && !path.isAbsolute(relative), 'Expected a fixture-owned path');
    return child;
}

async function verifyArtifact(directory, definition) {
    const name = path.posix.basename(new URL(definition.downloadUrl).pathname);
    assert.equal(name.includes('..'), false);
    const file = inside(directory, path.join(directory, name));
    const details = await fs.lstat(file);
    assert.ok(details.isFile() && !details.isSymbolicLink(), 'A previously downloaded artifact is required');
    assert.equal(details.size, definition.asset.sizeBytes, 'Pinned artifact size mismatch');
    assert.equal(await fingerprint(file), definition.sha256, 'Pinned artifact SHA-256 mismatch');
    return file;
}

async function copyNotices(root, target) {
    const source = path.join(root, 'docs/speech/licenses');
    const manifest = JSON.parse(await fs.readFile(path.join(source, 'manifest.json'), 'utf8'));
    assert.ok(Array.isArray(manifest.files) && manifest.files.length > 0 && manifest.files.length <= 64);
    assert.equal(new Set(manifest.files).size, manifest.files.length);
    const legal = path.join(target, 'legal');
    await fs.mkdir(legal, {recursive: true});
    for (const name of manifest.files) {
        assert.equal(typeof name, 'string');
        assert.ok(name && name.length <= 128 && !name.includes('..') && path.basename(name) === name);
        const original = inside(source, path.join(source, name));
        const info = await fs.lstat(original);
        assert.ok(info.isFile() && !info.isSymbolicLink() && info.size <= 2 * 1024 * 1024);
        await fs.copyFile(original, path.join(legal, name));
    }
}

async function installFixtures(root, profile, definitions, artifacts) {
    const speech = path.join(profile, 'speech');
    await fs.mkdir(speech);
    for (const definition of definitions) {
        const target = inside(speech, path.join(speech, definition.asset.id));
        await fs.mkdir(target);
        if (definition.asset.id === 'sherpa-runtime') {
            assert.equal(definition.archiveRoot, '');
            await fs.copyFile(artifacts.get(definition.asset.id), path.join(target, 'native.jar'));
        } else {
            assert.equal(definition.asset.id, VOICE_ID);
            assert.equal(definition.archiveRoot, 'vits-piper-ru_RU-irina-medium-int8');
            const tar = path.join(process.env.SystemRoot || 'C:/Windows', 'System32/tar.exe');
            // Only the exact pinned curated archive is extracted, with one fixed root stripped.
            await execute(tar, ['-xjf', artifacts.get(VOICE_ID), '--strip-components', '1', '-C', target], {
                windowsHide: true, timeout: 60_000, maxBuffer: 1024 * 1024,
            });
        }
        for (const required of definition.requiredFiles) {
            await fs.access(inside(target, path.join(target, required)));
        }
        await copyNotices(root, target);
        await fs.writeFile(path.join(target, 'artifact.sha256'), definition.sha256, {flag: 'wx'});
    }
}

async function startOllamaTrap() {
    const observed = {metadataRequests: 0, inferenceRequests: 0, unexpectedRequests: 0};
    const server = http.createServer((request, response) => {
        const route = new URL(request.url, 'http://127.0.0.1').pathname;
        const metadata = new Map([
            ['/api/tags', {models: []}], ['/api/ps', {models: []}],
            ['/api/version', {version: '0.0.0'}], ['/v1/models', {object: 'list', data: []}],
        ]);
        response.setHeader('Content-Type', 'application/json');
        if (request.method === 'GET' && metadata.has(route)) {
            observed.metadataRequests++;
            response.end(JSON.stringify(metadata.get(route)));
        } else if (request.method === 'GET' && route === '/') {
            observed.metadataRequests++;
            response.end('Ollama is running');
        } else if (request.method === 'POST' && route === '/api/show') {
            observed.metadataRequests++;
            response.writeHead(404).end(JSON.stringify({error: 'No conversation models in speech verification'}));
        } else {
            if (['/api/chat', '/api/generate', '/v1/chat/completions', '/v1/completions', '/v1/responses'].includes(route)) {
                observed.inferenceRequests++;
            } else {
                observed.unexpectedRequests++;
            }
            response.writeHead(503).end(JSON.stringify({error: 'Speech verification forbids model inference'}));
        }
        request.resume();
    });
    await new Promise((resolve, reject) => {
        server.once('error', reject);
        server.listen(0, '127.0.0.1', resolve);
    });
    return {
        observed,
        url: `http://127.0.0.1:${server.address().port}`,
        close: async () => {
            server.closeAllConnections();
            await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
        },
    };
}

async function json(page, route, body) {
    return page.evaluate(async ({route, body}) => {
        const response = await fetch(route, {
            method: body === undefined ? 'GET' : 'POST',
            ...(body === undefined ? {} : {headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)}),
            signal: AbortSignal.timeout(15_000),
        });
        if (!response.ok) throw new Error(`Speech smoke HTTP ${response.status}`);
        return response.json();
    }, {route, body});
}

async function waitForJob(page, initial) {
    const statuses = new Set([initial.status]);
    let job = initial;
    const deadline = Date.now() + INFERENCE_TIMEOUT_MS;
    while (!TERMINAL_STATUSES.has(job.status) && Date.now() < deadline) {
        await sleep(500);
        job = await json(page, `/api/preview/jobs/${encodeURIComponent(initial.id)}`);
        statuses.add(job.status);
    }
    assert.equal(job.status, 'ready', 'Real speech synthesis did not complete successfully');
    assert.equal(job.result?.text, SAMPLE);
    assert.equal(job.result?.model, VOICE_ID);
    assert.ok(job.result?.voice?.duration > 0 && job.result.voice.duration <= 600);
    return {voice: job.result.voice, statuses: [...statuses]};
}

async function saveAudio(page, voice, destination) {
    const bytes = await page.evaluate(async url => {
        const target = new URL(url, location.origin);
        if (target.origin !== location.origin || !target.pathname.startsWith('/api/speech/audio/')) {
            throw new Error('Unexpected speech audio origin');
        }
        const response = await fetch(target, {signal: AbortSignal.timeout(15_000)});
        if (!response.ok) throw new Error(`Speech audio HTTP ${response.status}`);
        return Array.from(new Uint8Array(await response.arrayBuffer()));
    }, voice.url);
    const audio = Buffer.from(bytes);
    assert.equal(audio.subarray(0, 4).toString('ascii'), 'OggS');
    assert.ok(audio.length > 100 && audio.length <= 64 * 1024 * 1024);
    await fs.writeFile(destination, audio, {flag: 'wx'});
    return audio.length;
}

async function settingsPlayback(page, destination) {
    await page.evaluate(() => window.dispatchEvent(new Event('aigram-settings')));
    const dialog = page.getByRole('dialog', {name: ru.settings, exact: true});
    await dialog.getByRole('menuitem', {name: ru.settingsCenter.sections.voice, exact: true}).click();
    await dialog.getByRole('slider', {name: ru.voice.speechRate, exact: true}).press('ArrowRight');
    const preview = dialog.locator('[data-qa="speech-preview"]');
    await preview.getByRole('textbox', {name: ru.voice.previewText, exact: true}).fill(SAMPLE);
    const responsePromise = page.waitForResponse(response =>
        new URL(response.url()).pathname === '/api/speech/preview' && response.request().method() === 'POST',
    {timeout: 15_000});
    await preview.getByRole('button', {name: ru.voice.previewCheck, exact: true}).click();
    const response = await responsePromise;
    assert.equal(response.status(), 200);
    assert.deepEqual(response.request().postDataJSON(), {text: SAMPLE, modelId: VOICE_ID, speed: 1.1});
    const completed = await waitForJob(page, await response.json());
    const player = preview.locator('audio');
    await player.waitFor({state: 'attached', timeout: 15_000});
    await page.waitForFunction(() => {
        const audio = document.querySelector('[data-qa="speech-preview"] audio');
        return audio && Number.isFinite(audio.duration) && audio.duration > 0 && audio.readyState >= 1;
    }, undefined, {timeout: 15_000});
    await preview.getByRole('button', {name: ru.voice.play, exact: true}).click();
    await page.waitForFunction(() => {
        const audio = document.querySelector('[data-qa="speech-preview"] audio');
        return audio && !audio.paused && audio.currentTime > 0;
    }, undefined, {timeout: 10_000});
    const playback = await player.evaluate(audio => ({duration: audio.duration, advanced: audio.currentTime > 0, error: Boolean(audio.error)}));
    assert.equal(playback.error, false);
    await player.evaluate(audio => audio.pause());
    await preview.scrollIntoViewIfNeeded();
    await dialog.screenshot({path: destination, animations: 'disabled'});
    return {voice: completed.voice, statuses: completed.statuses, playback};
}

async function descriptorRemoved(profile) {
    const descriptor = path.join(profile, 'launch.url');
    const deadline = Date.now() + 10_000;
    while (Date.now() < deadline) {
        try {
            await fs.access(descriptor);
        } catch (error) {
            if (error.code === 'ENOENT') return true;
            throw error;
        }
        await sleep(100);
    }
    throw new Error('Backend session descriptor survived packaged shutdown');
}

(async () => {
    assert.equal(process.platform, 'win32', 'The packaged speech smoke requires Windows x64');
    const root = await fs.realpath(path.resolve(__dirname, '../..'));
    const verification = path.join(root, 'build/speech-verification');
    assert.equal((await fs.realpath(verification)).toLowerCase(), verification.toLowerCase());
    const catalog = JSON.parse(await fs.readFile(path.join(root, 'backend/src/main/resources/speech/catalog.json'), 'utf8'));
    const definitions = ['sherpa-runtime', VOICE_ID].map(id => {
        const matches = catalog.filter(definition => definition.asset.id === id);
        assert.equal(matches.length, 1, 'Expected one pinned curated definition');
        return matches[0];
    });
    const artifacts = new Map();
    for (const definition of definitions) artifacts.set(definition.asset.id, await verifyArtifact(verification, definition));
    const executable = await fs.realpath(process.env.AIGRAM_TEST_EXECUTABLE || process.env.BOLTUNYA_TEST_EXECUTABLE || path.join(root, 'complie/win-unpacked/Aigram.exe'));
    assert.ok((await fs.stat(executable)).isFile());
    const profile = inside(verification, await fs.mkdtemp(path.join(verification, 'packaged-profile-')));
    const proofRoot = path.join(verification, 'packaged-proof');
    await fs.mkdir(proofRoot, {recursive: true});
    assert.equal((await fs.realpath(proofRoot)).toLowerCase(), proofRoot.toLowerCase());
    const proofDirectory = inside(proofRoot, await fs.mkdtemp(path.join(proofRoot, 'run-')));
    const report = {
        passed: false, stage: 'fixtures', executable: path.basename(executable), executableSha256: await fingerprint(executable),
        artifacts: definitions.map(definition => ({id: definition.asset.id, bytes: definition.asset.sizeBytes, sha256: definition.sha256})),
    };
    let application;
    let trap;
    let failure;
    const started = Date.now();
    try {
        await installFixtures(root, profile, definitions, artifacts);
        await fs.writeFile(path.join(profile, 'settings.json'), JSON.stringify({
            paused: true, closeToTray: false, launchAtLogin: false, resumeOnStartup: false,
            memoryEnabled: false, voiceInputEnabled: false, voiceReplyMode: 'text',
            voiceSynthesisModel: VOICE_ID, voiceSpeechRate: 1,
        }), {flag: 'wx'});
        trap = await startOllamaTrap();
        report.stage = 'packaged-startup';
        const environment = {...process.env, AIGRAM_DATA_DIR: profile, BOLTUNYA_DATA_DIR: profile,
            AIGRAM_OLLAMA_URL: trap.url, BOLTUNYA_OLLAMA_URL: trap.url,
            AIGRAM_SKIP_UPDATE_CHECK: 'true', BOLTUNYA_SKIP_UPDATE_CHECK: 'true'};
        delete environment.PORTABLE_EXECUTABLE_FILE;
        delete environment.PORTABLE_EXECUTABLE_DIR;
        delete environment.ELECTRON_RUN_AS_NODE;
        application = await electron.launch({executablePath: executable, env: environment, timeout: 60_000});
        const page = await application.firstWindow();
        await page.getByRole('button', {name: ru.connect, exact: true}).waitFor({timeout: 45_000});
        report.version = await application.evaluate(({app}) => app.getVersion());
        const before = await json(page, '/api/state');
        assert.notEqual(before.telegram.state, 'ready');
        assert.equal(before.telegram.userId, '');
        assert.equal(before.chats.length, 0);
        assert.equal(before.settings.paused, true);
        const assets = await json(page, '/api/speech');
        for (const definition of definitions) {
            assert.equal(assets.assets.find(asset => asset.id === definition.asset.id)?.installed, true);
        }
        report.stage = 'api-synthesis';
        const synthesisStarted = Date.now();
        const job = await json(page, '/api/speech/preview', {text: SAMPLE, modelId: VOICE_ID, speed: 1.1});
        const result = await waitForJob(page, job);
        report.apiSynthesisMs = Date.now() - synthesisStarted;
        report.queueStatuses = result.statuses;
        report.audioBytes = await saveAudio(page, result.voice, path.join(proofDirectory, 'api-preview.ogg'));
        report.stage = 'settings-playback';
        const uiStarted = Date.now();
        const ui = await settingsPlayback(page, path.join(proofDirectory, 'settings.png'));
        report.settingsSynthesisMs = Date.now() - uiStarted;
        report.settingsPlayback = ui.playback;
        report.settingsQueueStatuses = ui.statuses;
        report.settingsAudioBytes = await saveAudio(page, ui.voice, path.join(proofDirectory, 'settings-preview.ogg'));
        const after = await json(page, '/api/state');
        assert.notEqual(after.telegram.state, 'ready');
        assert.equal(after.telegram.userId, '');
        assert.equal(after.chats.length, 0);
        assert.deepEqual(after.settings, before.settings, 'Preview must not save changed speech preferences');
        assert.equal(trap.observed.inferenceRequests, 0, 'Speech preview attempted conversation-model inference');
        assert.equal(trap.observed.unexpectedRequests, 0, 'Speech preview attempted an unexpected model operation');
        report.telegramSignedOut = true;
        report.noConversationInference = true;
        report.realBundledRuntimeAndCodec = true;
        report.stage = 'shutdown';
    } catch (error) {
        failure = error;
        report.error = error.name;
    } finally {
        try {
            if (application) {
                await application.close();
                report.sessionDescriptorRemoved = await descriptorRemoved(profile);
            }
        } catch (error) {
            failure = failure ? new AggregateError([failure, error], 'Packaged speech cleanup failed') : error;
            report.shutdownError = error.name;
        } finally {
            if (trap) {
                report.modelRequests = trap.observed;
                try {
                    await trap.close();
                    assert.equal(trap.observed.inferenceRequests, 0, 'Conversation inference occurred during the smoke');
                    assert.equal(trap.observed.unexpectedRequests, 0, 'Unexpected model operation occurred during the smoke');
                }
                catch (error) {
                    failure = failure ? new AggregateError([failure, error], 'Model trap cleanup failed') : error;
                    report.trapShutdownError = error.name;
                }
            }
            report.durationMs = Date.now() - started;
            report.passed = !failure;
            if (!failure) report.stage = 'complete';
            // This fresh synthetic profile and evidence are retained; no actual application data is touched.
            await fs.writeFile(path.join(proofDirectory, 'proof.json'), JSON.stringify(report, null, 2), {flag: 'wx'});
        }
    }
    console.log(JSON.stringify({passed: report.passed, stage: report.stage, durationMs: report.durationMs,
        proof: path.relative(root, proofDirectory), isolatedProfile: path.relative(root, profile)}));
    if (failure) throw failure;
})().catch(error => {
    // Do not print launch URLs/session cookies or child-process argument lists on failures.
    console.error('Packaged speech smoke failed:', error.name, '(see the synthetic packaged-proof report).');
    process.exitCode = 1;
});
