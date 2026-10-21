import type {Activity} from '@/shared/api/client';

export function visibleChatActivity(entries: Activity[], chatId: string | null) {
    const conversation = entries.filter((entry) => entry.chatId === chatId && entry.kind !== 'memory');
    const active = conversation.find((entry) => ['generating', 'queued'].includes(entry.status));
    if (active) {
        return active;
    }
    const latest = [...conversation].sort((left, right) => right.date - left.date)[0];
    return latest && ['error', 'interrupted'].includes(latest.status) ? latest : undefined;
}
