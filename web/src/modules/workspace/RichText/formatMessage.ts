import type {MessageEntity} from '@/shared/api/client';

export const structuralTypes = new Set(['pre', 'precode', 'quote', 'expandablequote', 'spoiler']);

export function safeLink(value: string): string | undefined {
    const source = /^(www\.|t\.me\/)/i.test(value) ? 'https://' + value : value;
    try {
        const url = new URL(source);
        return ['https:', 'http:', 'mailto:', 'tel:', 'tg:'].includes(url.protocol) ? url.href : undefined;
    } catch {
        return undefined;
    }
}

export function prepareEntities(text: string, entities: MessageEntity[] = []): MessageEntity[] {
    const valid = entities.filter(
        (entity) => entity.offset >= 0 && entity.length > 0 && entity.offset + entity.length <= text.length,
    );
    const urls = Array.from(text.matchAll(/(?:https?:\/\/|www\.|t\.me\/)[^\s<>]+/gi)).flatMap((match) => {
        const value = match[0].replace(/[.,!?;:]+$/, '');
        const offset = match.index;
        const covered = valid.some(
            (entity) =>
                ['url', 'texturl', 'code', 'pre', 'precode'].includes(entity.type) &&
                entity.offset < offset + value.length &&
                entity.offset + entity.length > offset,
        );
        return covered ? [] : [{offset, length: value.length, type: 'url'}];
    });
    return [...valid, ...urls];
}

export function inlineSegments(text: string, entities: MessageEntity[], start = 0, end = text.length) {
    const boundaries = [
        ...new Set([
            start,
            end,
            ...entities
                .flatMap((entity) => [entity.offset, entity.offset + entity.length])
                .filter((value) => value > start && value < end),
        ]),
    ].sort((a, b) => a - b);
    return boundaries.slice(0, -1).map((offset, index) => ({
        offset,
        text: text.slice(offset, boundaries[index + 1]),
        entities: entities.filter(
            (entity) =>
                !structuralTypes.has(entity.type) &&
                entity.offset <= offset &&
                entity.offset + entity.length >= boundaries[index + 1],
        ),
    }));
}

export function entityLink(entity: MessageEntity, text: string): string | undefined {
    const value = text.slice(entity.offset, entity.offset + entity.length);
    const source =
        entity.type === 'email'
            ? 'mailto:' + value
            : entity.type === 'phone'
              ? 'tel:' + value.replace(/[^+\d]/g, '')
              : entity.type === 'mention'
                ? 'https://t.me/' + value.replace(/^@/, '')
                : entity.url || value;
    return safeLink(source);
}
