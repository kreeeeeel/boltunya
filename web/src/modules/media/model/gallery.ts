import type {Message} from '@/shared/api/client';

export function isGalleryMessage(message: Message): boolean {
    return Boolean(
        message.media &&
            message.kind !== 'sticker' &&
            (message.media.mimeType.startsWith('image/') || message.media.mimeType.startsWith('video/')),
    );
}

export function galleryMessages(messages: Message[]): Message[] {
    return [
        ...new Map(messages.filter(isGalleryMessage).map((message) => [message.id, message])).values(),
    ].sort(
        (left, right) =>
            left.date - right.date || left.id.length - right.id.length || left.id.localeCompare(right.id),
    );
}

export function mediaSource(message: Message, thumbnail = false): string {
    const media = message.media;
    if (!media) {
        return '';
    }
    const fileId = thumbnail ? media.thumbnailFileId || (media.downloaded ? media.fileId : 0) : media.fileId;
    return fileId ? `/api/media/${fileId}${media.cacheVersion ? '?v=' + media.cacheVersion : ''}` : '';
}

export function validMediaAddress(chatId: string, messageId: string): boolean {
    try {
        const chat = BigInt(chatId);
        const message = BigInt(messageId);
        const max = 9223372036854775807n;
        return (
            chatId === String(chat) &&
            messageId === String(message) &&
            chat !== 0n &&
            chat >= -max - 1n &&
            chat <= max &&
            message > 0n &&
            message <= max
        );
    } catch {
        return false;
    }
}
