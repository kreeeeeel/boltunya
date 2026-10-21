import {afterEach, expect, it, vi} from 'vitest';
import {api, defaultRule} from '@/shared/api/client';
import {ApiRequestError} from '@/shared/api/ApiRequestError';

afterEach(() => vi.unstubAllGlobals());

it('uses generated operations with same-origin cookies and preserves opaque Telegram identifiers', async () => {
    const fetch = vi
        .fn()
        .mockResolvedValue(new Response('[]', {headers: {'Content-Type': 'application/json'}}));
    vi.stubGlobal('fetch', fetch);
    const signal = new AbortController().signal;
    await api.chats.history({chatId: '-1001234567890', before: '9007199254740993123'}, {signal});
    expect(fetch).toHaveBeenCalledWith(
        '/api/chats/-1001234567890/messages?before=9007199254740993123',
        expect.objectContaining({method: 'GET', credentials: 'same-origin', signal}),
    );
});

it('sends generated JSON request bodies without adding defaults to partial settings updates', async () => {
    const fetch = vi
        .fn()
        .mockResolvedValue(new Response('{}', {headers: {'Content-Type': 'application/json'}}));
    vi.stubGlobal('fetch', fetch);
    await api.settings.settings({settingsUpdate: {paused: false}});
    expect(fetch).toHaveBeenCalledWith(
        '/api/settings',
        expect.objectContaining({
            method: 'PATCH',
            body: JSON.stringify({paused: false}),
        }),
    );
    await api.models.preview({
        preview: {chatId: '42', text: '', rule: {...defaultRule, instructions: 'Коротко.'}},
    });
    expect(fetch).toHaveBeenLastCalledWith(
        '/api/preview',
        expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({chatId: '42', text: '', rule: {...defaultRule, instructions: 'Коротко.'}}),
        }),
    );
});

it('normalizes safe API errors and retains status and machine code', async () => {
    vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
            new Response(JSON.stringify({message: 'Сначала выберите модель.', code: 'MODEL_NOT_READY'}), {
                status: 409,
                headers: {'Content-Type': 'application/json'},
            }),
        ),
    );
    await expect(api.models.start()).rejects.toMatchObject({
        name: 'ApiRequestError',
        message: 'Сначала выберите модель.',
        status: 409,
        code: 'MODEL_NOT_READY',
    });
});

it('reports HTTP status for non-JSON errors instead of exposing an HTML body', async () => {
    vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(new Response('<html>Unavailable</html>', {status: 502})),
    );
    await expect(api.system.state()).rejects.toEqual(new ApiRequestError('HTTP 502', 502, ''));
});

it('preserves cancellation and reads binary media through the generated operation', async () => {
    const cancelled = new DOMException('Cancelled', 'AbortError');
    const fetch = vi.fn().mockRejectedValueOnce(cancelled).mockResolvedValueOnce(new Response('{"v":"5"}'));
    vi.stubGlobal('fetch', fetch);
    await expect(api.system.state()).rejects.toBe(cancelled);
    const media = await api.media.media({fileId: 17});
    const text = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsText(media);
    });
    expect(text).toBe('{"v":"5"}');
    expect(fetch).toHaveBeenLastCalledWith('/api/media/17', expect.objectContaining({method: 'GET'}));
});
