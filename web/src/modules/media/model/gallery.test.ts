import {expect, it} from 'vitest';
import type {Message} from '@/shared/api/client';
import {galleryMessages, mediaSource, validMediaAddress} from '@/modules/media/model/gallery';

const photo: Message = {
    id: '1',
    chatId: '-100',
    text: '',
    date: 1,
    outgoing: false,
    kind: 'photo',
    media: {
        fileId: 7,
        mimeType: 'image/jpeg',
        fileName: 'photo.jpg',
        width: 400,
        height: 300,
        thumbnailFileId: 8,
        size: 10,
        cacheVersion: 2,
    },
};

it('keeps photos and videos in chronological order and deduplicates message identifiers', () => {
    expect(
        galleryMessages([
            {...photo, id: '3', date: 3},
            photo,
            photo,
            {...photo, id: '2', date: 2, kind: 'sticker'},
            {...photo, id: '4', kind: 'text', media: undefined},
        ]).map((message) => message.id),
    ).toEqual(['1', '3']);
});

it('never downloads a full uncached neighbour as its thumbnail', () => {
    expect(mediaSource(photo, true)).toBe('/api/media/8?v=2');
    expect(
        mediaSource({...photo, media: {...photo.media!, thumbnailFileId: 0, downloaded: false}}, true),
    ).toBe('');
    expect(mediaSource(photo)).toBe('/api/media/7?v=2');
});

it('accepts only canonical Telegram int64 addresses for the standalone window', () => {
    expect(validMediaAddress('-100', '9223372036854775807')).toBe(true);
    for (const [chat, message] of [
        ['0', '1'],
        ['1', '-1'],
        ['1', '9223372036854775808'],
        ['+1', '1'],
        ['1', '1e3'],
        ['1/../../', '2'],
        ['01', '1'],
    ]) {
        expect(validMediaAddress(chat, message)).toBe(false);
    }
});
