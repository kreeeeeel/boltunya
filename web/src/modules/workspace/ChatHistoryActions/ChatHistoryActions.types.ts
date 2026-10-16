export interface ChatHistoryActionsProps {
    readonly chatId: string;
    readonly onCollectContext: () => void;
    readonly onDeleted: () => void;
    readonly disabled?: boolean;
}
