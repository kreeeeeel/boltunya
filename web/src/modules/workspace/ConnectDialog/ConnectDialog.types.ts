import type {Telegram} from '@/shared/api/client';

export interface ConnectDialogProps {
    readonly status: Telegram | undefined;
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}
