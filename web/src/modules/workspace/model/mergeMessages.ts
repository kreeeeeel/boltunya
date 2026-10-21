import type {Message} from '@/shared/api/client';

export function mergeMessages(previous: Message[], current: Message[]): Message[] {
    const merged = new Map(previous.map((message) => [`${message.chatId}:${message.id}`, message]));
    current.forEach((message) => {
        const key = `${message.chatId}:${message.id}`;
        const previousMessage = merged.get(key);
        merged.set(
            key,
            previousMessage?.deleted && !message.deleted
                ? {...message, deleted: true, deletedAt: previousMessage.deletedAt}
                : message,
        );
    });
    return [...merged.values()].sort(
        (a, b) => a.date - b.date || a.id.length - b.id.length || a.id.localeCompare(b.id),
    );
}

/** Refreshes the authoritative latest page while retaining older, paginated history. */
export function mergeRecentMessages(previous: Message[], current: Message[]): Message[] {
    if (current.length === 0) {
        return previous;
    }
    const oldest = current.reduce((left, right) =>
        compareIdentifiers(left.id, right.id) <= 0 ? left : right,
    );
    return mergeMessages(
        previous.filter(
            (message) =>
                message.chatId === oldest.chatId &&
                (message.deleted || compareIdentifiers(message.id, oldest.id) < 0),
        ),
        current,
    );
}

function compareIdentifiers(left: string, right: string): number {
    return left.length - right.length || left.localeCompare(right);
}
