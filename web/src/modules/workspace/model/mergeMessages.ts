import type {Message} from '@/shared/api/client';
import {shareSnapshot} from '@/modules/workspace/model/shareSnapshot';

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
    // Unchanged polling should inspect only the latest page, not the paginated history.
    const offset = previous.length - current.length;
    const preceding = previous[offset - 1];
    if (
        offset >= 0 &&
        previous[0]?.chatId === oldest.chatId &&
        (!preceding || preceding.deleted || compareIdentifiers(preceding.id, oldest.id) < 0)
    ) {
        const recent = previous.slice(offset);
        if (shareSnapshot(recent, current) === recent) {
            return previous;
        }
    }
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
