import type {Engine} from '@/shared/api/client';

export interface ModelsPanelProps {
    readonly embedded?: boolean;
    readonly engine: Engine | undefined;
    readonly defaultModel: string;
    readonly defaultModelProfileId?: string;
    readonly onRefresh: () => Promise<void>;
}
