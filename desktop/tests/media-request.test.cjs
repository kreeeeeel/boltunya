const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mediaViewerUrl} = require('../dist/media-request.js');

test('builds a same-origin viewer route from Telegram identifiers without copying a session key', () => {
    const url = new URL(mediaViewerUrl('http://127.0.0.1:12345', {chatId: '-100123', messageId: '456'}));
    assert.equal(url.origin, 'http://127.0.0.1:12345');
    assert.equal(url.pathname, '/media-viewer/');
    assert.equal(url.search, '?chatId=-100123&messageId=456');
});

test('rejects arbitrary URLs, malformed identifiers, zero and out-of-range Telegram IDs', () => {
    for (const origin of ['https://example.com', 'http://localhost:1234', 'http://127.0.0.1:1234/?key=secret', 'http://127.0.0.1:1234/api/state']) {
        assert.throws(() => mediaViewerUrl(origin, {chatId: '42', messageId: '1'}), error => error.message.includes('authenticated local backend'));
    }
    for (const request of [null, [], {chatId: 'https://example.com', messageId: '1'}, {chatId: '0', messageId: '1'}, {chatId: '+42', messageId: '1'}, {chatId: '42\n', messageId: '1'}, {chatId: '42', messageId: '-1'}, {chatId: '42', messageId: '0'}, {chatId: '9223372036854775808', messageId: '1'}]) {
        assert.throws(() => mediaViewerUrl('http://127.0.0.1:12345', request), error => error.message.includes('identifiers'));
    }
});
