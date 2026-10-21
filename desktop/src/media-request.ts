const LOWEST_CHAT_ID = -(1n << 63n);
const HIGHEST_ID = (1n << 63n) - 1n;

function numericId(value: unknown, chat: boolean): value is string {
    if (typeof value !== 'string' || value.length > 20) return false;
    const digits = value.startsWith('-') ? value.slice(1) : value;
    if (!digits || !Array.from(digits).every(char => char >= '0' && char <= '9')) return false;
    const number = BigInt(value);
    return number !== 0n && number <= HIGHEST_ID && number >= (chat ? LOWEST_CHAT_ID : 1n);
}

export function mediaViewerUrl(origin: string, request: unknown): string {
    const base = new URL(origin);
    if (base.protocol !== 'http:' || base.hostname !== '127.0.0.1' || !base.port || base.origin !== origin) throw new Error('Media viewer requires the authenticated local backend');
    if (!request || typeof request !== 'object' || Array.isArray(request)) throw new Error('Media identifiers are required');
    const identifiers = request as Record<string, unknown>;
    if (!numericId(identifiers.chatId, true) || !numericId(identifiers.messageId, false)) throw new Error('Media identifiers are invalid');
    const target = new URL('/media-viewer/', base);
    target.searchParams.set('chatId', identifiers.chatId);
    target.searchParams.set('messageId', identifiers.messageId);
    return target.toString();
}
