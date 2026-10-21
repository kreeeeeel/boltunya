import type {Message} from '@/shared/api/client';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
const MAX_IDENTIFIER_LENGTH = 19;
function identifier(value: string) {
    if (
        !value ||
        value.length > MAX_IDENTIFIER_LENGTH ||
        !Array.from(value).every((character) => '0123456789'.includes(character))
    ) {
        return null;
    }
    return BigInt(value);
}
export function applyContextExclusion(
    messages: Message[],
    chatId: string,
    change: ContextExclusionChange,
): Message[] {
    const boundary = 'reset' in change ? null : identifier(change.messageId);
    return messages.map((message) => {
        if (message.chatId !== chatId) {
            return message;
        }
        if ('reset' in change) {
            return {...message, contextExcluded: false};
        }
        const id = identifier(message.id);
        const matches =
            change.scope === 'message'
                ? message.id === change.messageId
                : id !== null &&
                  boundary !== null &&
                  (change.scope === 'through' ? id <= boundary : id >= boundary);
        return matches ? {...message, contextExcluded: change.excluded} : message;
    });
}
