import type {Activity} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

export const historyStorage: components['schemas']['ActivityHistoryStorage'] = {
    retentionDays: 0,
    maxStorageMb: 0,
    bytes: 4096,
    recording: true,
    errorCode: '',
};
export function historyPage(entries: Activity[] = []): components['schemas']['ActivityHistoryPage'] {
    return {
        entries: entries.filter((entry) => !['queued', 'generating'].includes(entry.status)),
        nextCursor: null,
        hasMore: false,
        total: entries.length,
        storage: historyStorage,
    };
}
