import type {Message} from '@/shared/api/client';
import {messageDay} from '@/modules/workspace/model/messagePresentation';

const GROUP_INTERVAL_SECONDS = 300;

export function messagesGrouped(message: Message, other?: Message, group = message.group): boolean {
    if (
        !other ||
        other.outgoing !== message.outgoing ||
        (other.topicId ?? 0) !== (message.topicId ?? 0) ||
        Math.abs(other.date - message.date) >= GROUP_INTERVAL_SECONDS ||
        messageDay(other.date) !== messageDay(message.date)
    ) {
        return false;
    }
    if (!group || message.outgoing) {
        return true;
    }
    return message.senderId
        ? message.senderId === other.senderId
        : !!message.senderName && message.senderName === other.senderName;
}
