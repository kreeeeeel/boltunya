import type {Message} from '@/shared/api/client';

export function mergeMessages(previous: Message[], current: Message[]): Message[] {
    const merged = new Map(previous.map((message) => [`${message.chatId}:${message.id}`, message]));
    current.forEach((message) => {
        const key = `${message.chatId}:${message.id}`;
        const previousMessage = merged.get(key);
        const next =
            previousMessage?.deleted && !message.deleted
                ? {...message, deleted: true, deletedAt: previousMessage.deletedAt}
                : message;
        merged.set(
            key,
            previousMessage?.editedAt && previousMessage.editedAt > (message.editedAt ?? 0)
                ? {
                      ...next,
                      text: previousMessage.text,
                      entities: previousMessage.entities,
                      editedAt: previousMessage.editedAt,
                  }
                : next,
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
    const currentIds = new Set(current.map((message) => message.id));
    return mergeMessages(
        previous.filter(
            (message) =>
                message.chatId === oldest.chatId &&
                (message.deleted ||
                    currentIds.has(message.id) ||
                    compareIdentifiers(message.id, oldest.id) < 0),
        ),
        current,
    );
}

function compareIdentifiers(left: string, right: string): number {
    return left.length - right.length || left.localeCompare(right);
}
