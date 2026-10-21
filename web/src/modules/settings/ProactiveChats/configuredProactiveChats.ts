import type {AppState, Chat} from '@/shared/api/client';

export function configuredProactiveChats(
    chats: Chat[],
    settings: AppState['settings'],
    ownerId = '',
): Chat[] {
    return chats.filter((chat) => {
        const mode = chat.rule.proactiveMode ?? 'inherit';
        const enabled =
            mode === 'enabled' || (mode === 'inherit' && settings.proactiveEnabled && chat.rule.enabled);
        if (
            !enabled ||
            chat.id === ownerId ||
            chat.joined === false ||
            chat.kind === 'channel' ||
            !chat.lastMessageId
        ) {
            return false;
        }
        const groupMode =
            !chat.rule.groupReplies || chat.rule.groupReplies === 'inherit'
                ? settings.groupReplies
                : chat.rule.groupReplies;
        return (
            chat.kind === 'private' || groupMode === 'all' || (groupMode === 'mentions' && mode === 'enabled')
        );
    });
}
