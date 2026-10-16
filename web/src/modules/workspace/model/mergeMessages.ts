import type {Message} from '@/shared/api/client';

export function mergeMessages(previous: Message[], current: Message[]): Message[] {
    const merged = new Map(previous.map((message) => [message.id, message]));
    current.forEach((message) => merged.set(message.id, message));
    return [...merged.values()].sort(
        (a, b) => a.date - b.date || a.id.length - b.id.length || a.id.localeCompare(b.id),
    );
}
