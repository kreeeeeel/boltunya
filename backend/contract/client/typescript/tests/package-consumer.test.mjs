import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import {
    copyFileSync,
    mkdirSync,
    mkdtempSync,
    readFileSync,
    rmSync,
    writeFileSync,
} from "node:fs";
import { basename, dirname, join, relative, resolve } from "node:path";
import { after, before, test } from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";

const directory = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const consumerRoot = join(directory, "build/consumer-tests");
let consumer;
let sdk;

before(async () => {
    const manifest = JSON.parse(
        readFileSync(join(directory, "build/package/manifest.json"), "utf8"),
    );
    assert.equal(manifest.name, "@aigram/api-client");
    assert.equal(basename(manifest.filename), manifest.filename);
    assert.ok(manifest.filename.endsWith(".tgz"));
    const npm = process.env.npm_execpath;
    assert.ok(npm, "Run the consumer smoke through npm run test:consumer.");
    mkdirSync(consumerRoot, { recursive: true });
    consumer = mkdtempSync(join(consumerRoot, "install-"));
    writeFileSync(
        join(consumer, "package.json"),
        JSON.stringify({
            name: "aigram-sdk-smoke",
            version: "0.0.0",
            type: "module",
            private: true,
        }),
    );
    execFileSync(
        process.execPath,
        [
            npm,
            "install",
            join(directory, "build/package", manifest.filename),
            "--offline",
            "--ignore-scripts",
            "--no-audit",
            "--no-fund",
        ],
        { cwd: consumer, stdio: "pipe" },
    );
    const installed = join(consumer, "node_modules/@aigram/api-client");
    const installedMetadata = JSON.parse(
        readFileSync(join(installed, "package.json"), "utf8"),
    );
    assert.equal(installedMetadata.version, manifest.version);
    assert.equal(installedMetadata.types, "./index.d.ts");
    sdk = await import(pathToFileURL(join(installed, "index.js")).href);
});

after(() => {
    if (!consumer) {
        return;
    }
    const child = relative(consumerRoot, consumer);
    assert.equal(dirname(child), ".");
    assert.ok(basename(child).startsWith("install-"));
    rmSync(consumer, { recursive: true, force: true });
});

test("the installed tarball exposes all 76 operations in eight API groups and compiles typed imports", () => {
    const apis = Object.values(sdk).filter(
        (value) =>
            typeof value === "function" &&
            value.prototype instanceof sdk.BaseAPI,
    );
    assert.equal(apis.length, 8);
    const operationCount = apis.reduce(
        (count, api) =>
            count +
            Object.getOwnPropertyNames(api.prototype).filter((name) =>
                name.endsWith("RequestOpts"),
            ).length,
        0,
    );
    assert.equal(operationCount, 76);
    assert.equal(typeof sdk.ModelsApi.prototype.restartEngine, "function");
    copyFileSync(
        join(directory, "tests/consumer/consumer.ts"),
        join(consumer, "consumer.ts"),
    );
    copyFileSync(
        join(directory, "tests/consumer/tsconfig.json"),
        join(consumer, "tsconfig.json"),
    );
    execFileSync(
        process.execPath,
        [
            join(directory, "node_modules/typescript/bin/tsc"),
            "--project",
            join(consumer, "tsconfig.json"),
        ],
        { cwd: consumer, stdio: "pipe" },
    );
});

test("message edits keep exact text, UTF-16 ranges and opaque entity references in the installed SDK", async () => {
    const chatId = "-1009007199254740993555";
    const messageId = "9007199254740993999";
    const revision = "b".repeat(64);
    const text = "  😀 **literal**\n\nlink...\n";
    const entities = [
        { offset: 2, length: 2, kind: "existing", sourceIndex: 0, typeHint: "customemoji" },
        {
            offset: 18,
            length: 4,
            kind: "texturl",
            sourceIndex: null,
            url: "https://example.org/a?x=1&y=2",
        },
    ];
    const options = { text, entities, revision, kind: "caption", maxLength: 1024 };
    const updated = {
        id: messageId,
        chatId,
        text,
        outgoing: true,
        date: 1792578000,
        kind: "photo",
        editedAt: 1792578010,
    };
    const requests = [];
    const api = new sdk.ChatsApi(
        new sdk.Configuration({
            basePath: "http://127.0.0.1:9000",
            fetchApi: async (url, init) => {
                requests.push({ url, init });
                return Response.json(init.method === "GET" ? options : updated);
            },
        }),
    );

    assert.deepEqual(await api.messageEditOptions({ chatId, messageId }), options);
    const request = { text, entities, revision };
    assert.deepEqual(
        await api.editMessage({ chatId, messageId, messageEditRequest: request }),
        updated,
    );
    assert.equal(requests.length, 2);
    requests.forEach(({ url }) =>
        assert.equal(
            url,
            `http://127.0.0.1:9000/api/chats/${chatId}/messages/${messageId}/edit`,
        ),
    );
    assert.equal(requests[0].init.method, "GET");
    assert.equal(requests[0].init.body, undefined);
    assert.equal(requests[1].init.method, "PUT");
    assert.deepEqual(JSON.parse(requests[1].init.body), request);
});

test("the typed model client exposes the approved app-managed engine restart operation", async () => {
    const requests = [];
    const api = new sdk.ModelsApi(
        new sdk.Configuration({
            basePath: "http://127.0.0.1:9000",
            fetchApi: async (url, init) => {
                requests.push({ url, init });
                return Response.json({ ok: true });
            },
        }),
    );

    assert.deepEqual(await api.restartEngine(), { ok: true });
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, "http://127.0.0.1:9000/api/engine/restart");
    assert.equal(requests[0].init.method, "POST");
    assert.equal(requests[0].init.body, undefined);
});

test("history keeps large string IDs, nullable fields, unknown properties and explicit auth configuration", async () => {
    const message = {
        id: "9007199254740993999",
        chatId: "-1009007199254740993555",
        text: "A synthetic message",
        outgoing: false,
        date: 1792578000,
        kind: "text",
        media: null,
        serverExtension: { preserved: true },
    };
    const requests = [];
    const api = new sdk.ChatsApi(
        new sdk.Configuration({
            basePath: "http://127.0.0.1:9000",
            headers: { Cookie: "tiho=synthetic-session" },
            credentials: "include",
            fetchApi: async (url, init) => {
                requests.push({ url, init });
                return Response.json([message]);
            },
        }),
    );
    const result = await api.history({
        chatId: message.chatId,
        before: message.id,
    });
    assert.deepEqual(result, [message]);
    assert.equal(requests.length, 1);
    assert.equal(
        requests[0].url,
        `http://127.0.0.1:9000/api/chats/${message.chatId}/messages?before=${message.id}`,
    );
    assert.equal(requests[0].init.method, "GET");
    assert.equal(
        new Headers(requests[0].init.headers).get("Cookie"),
        "tiho=synthetic-session",
    );
    assert.equal(requests[0].init.credentials, "include");
});

test("JSON requests use the declared body and content type with an explicitly relative browser base path", async () => {
    const requests = [];
    const api = new sdk.ChatsApi(
        new sdk.Configuration({
            basePath: "",
            credentials: "same-origin",
            fetchApi: async (url, init) => {
                requests.push({ url, init });
                return Response.json({ ok: true });
            },
        }),
    );
    await api.send({ chatId: "42", text: { text: "Hello" } });
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, "/api/chats/42/messages");
    assert.equal(requests[0].init.method, "POST");
    assert.equal(
        new Headers(requests[0].init.headers).get("Content-Type"),
        "application/json",
    );
    assert.deepEqual(JSON.parse(requests[0].init.body), { text: "Hello" });
    assert.equal(requests[0].init.credentials, "same-origin");
});

test("a missing base path fails before fetch instead of choosing a server or reading window", async () => {
    let requests = 0;
    const api = new sdk.SystemApi(
        new sdk.Configuration({
            fetchApi: async () => {
                requests++;
                throw new Error(
                    "The SDK must not fetch without an explicit address.",
                );
            },
        }),
    );
    assert.equal(typeof globalThis.window, "undefined");
    await assert.rejects(
        api.state(),
        (error) =>
            error instanceof sdk.RequiredError && error.field === "basePath",
    );
    assert.equal(requests, 0);
});

test("binary uploads and downloads retain their bytes without JSON conversion", async () => {
    const bytes = new Uint8Array([137, 80, 78, 71, 0, 255, 10, 42]);
    const body = new Blob([bytes], { type: "image/png" });
    const requests = [];
    const api = new sdk.MediaApi(
        new sdk.Configuration({
            basePath: "http://127.0.0.1:9000",
            fetchApi: async (url, init) => {
                requests.push({ url, init });
                if (init.method === "POST") {
                    return Response.json({
                        id: "synthetic-upload",
                        name: "photo one.png",
                        mimeType: "image/png",
                        size: bytes.length,
                    });
                }
                return new Response(bytes, {
                    headers: { "Content-Type": "image/png" },
                });
            },
        }),
    );
    const uploaded = await api.uploadFile({
        xFileName: encodeURIComponent("photo one.png"),
        body,
    });
    assert.equal(uploaded.id, "synthetic-upload");
    assert.equal(requests[0].url, "http://127.0.0.1:9000/api/uploads");
    assert.equal(requests[0].init.method, "POST");
    assert.equal(requests[0].init.body, body);
    assert.equal(
        new Headers(requests[0].init.headers).get("Content-Type"),
        "application/octet-stream",
    );
    assert.equal(
        new Headers(requests[0].init.headers).get("X-File-Name"),
        "photo%20one.png",
    );
    const downloaded = await api.media({ fileId: 42 });
    assert.equal(requests.length, 2);
    assert.equal(requests[1].url, "http://127.0.0.1:9000/api/media/42");
    assert.equal(requests[1].init.method, "GET");
    assert.ok(downloaded instanceof Blob);
    assert.deepEqual(new Uint8Array(await downloaded.arrayBuffer()), bytes);
});

test("HTTP failures expose the original response without retries", async () => {
    let requests = 0;
    const failure = {
        message: "Synthetic authorization error",
        code: "session-required",
    };
    const api = new sdk.SystemApi(
        new sdk.Configuration({
            basePath: "http://127.0.0.1:9000",
            fetchApi: async () => {
                requests++;
                return Response.json(failure, { status: 401 });
            },
        }),
    );
    let reported;
    await assert.rejects(api.state(), (error) => {
        assert.ok(error instanceof sdk.ResponseError);
        assert.equal(error.response.status, 401);
        reported = error;
        return true;
    });
    assert.deepEqual(await reported.response.json(), failure);
    assert.equal(requests, 1);
});

test("formatting and idle follow-up fields survive the installed SDK HTTP transport", async () => {
    const requests = [];
    const configuration = new sdk.Configuration({
        basePath: "http://127.0.0.1:9000",
        fetchApi: async (url, init) => {
            requests.push({ url, body: JSON.parse(init.body) });
            return Response.json({ ok: true, formatReplies: true });
        },
    });
    await new sdk.SettingsApi(configuration).settings({
        settingsUpdate: { formatReplies: true, replyProcessingMode: "each" },
    });
    const chats = new sdk.ChatsApi(configuration);
    await chats.rule({
        chatId: "42",
        chatRule: {
            enabled: false,
            replyInterruptionMode: "finish",
            proactiveMode: "enabled",
            proactiveMessageMode: "text",
            proactiveText: "  How are you? 😀  ",
            proactiveIntervalMinutes: 90,
        },
    });
    const entities = [{ offset: 3, length: 5, type: "spoiler" }];
    await chats.send({ chatId: "42", text: { text: "😀 hello", entities } });
    await chats.sendAttachments({
        chatId: "42",
        sendMediaRequest: {
            uploadIds: ["synthetic"], kind: "photo",
            caption: "😀 hello", captionEntities: entities,
        },
    });
    assert.deepEqual(requests[0].body, { formatReplies: true, replyProcessingMode: "each" });
    assert.equal(requests[1].body.replyInterruptionMode, "finish");
    assert.equal(requests[1].body.proactiveMessageMode, "text");
    assert.equal(requests[1].body.proactiveText, "  How are you? 😀  ");
    assert.equal(requests[1].body.enabled, false);
    assert.deepEqual(requests[2].body.entities, entities);
    assert.deepEqual(requests[3].body.captionEntities, entities);
});

test("network failures preserve their cause and do not retry", async () => {
    let requests = 0;
    const cause = new Error("Synthetic connection failure");
    const api = new sdk.SystemApi(
        new sdk.Configuration({
            basePath: "http://127.0.0.1:9000",
            fetchApi: async () => {
                requests++;
                throw cause;
            },
        }),
    );
    await assert.rejects(
        api.state(),
        (error) => error instanceof sdk.FetchError && error.cause === cause,
    );
    assert.equal(requests, 1);
});
