import type {Chat} from '@/shared/api/client';

export interface ChatListProps {
    readonly chats: Chat[];
    readonly selected: string | null;
    readonly loading?: boolean;
    readonly onSelect: (id: string) => void;
}
