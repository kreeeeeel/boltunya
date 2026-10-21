import type {ReactNode} from 'react';

export interface StatusNoticeProps {
    readonly title: string;
    readonly description: string;
    readonly variant?: 'error' | 'cancelled';
    readonly actions?: ReactNode;
    readonly className?: string;
    readonly onClose?: () => void;
}
