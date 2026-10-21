import type {Message} from '@/shared/api/client';

export function applyUpdatedMessage(messages: Message[], updated: Message): Message[] {
    return messages.map((message) => {
        if (message.chatId !== updated.chatId || message.id !== updated.id) {
            return message;
        }

        return {
            ...message,
            ...updated,
            automated: updated.automated ?? message.automated,
            contextExcluded: updated.contextExcluded ?? message.contextExcluded,
            ...(message.deleted ? {deleted: true, deletedAt: message.deletedAt} : {}),
        };
    });
}
