import type {Activity} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
export const historyStorage: Contract.ActivityHistoryStorage = {
    retentionDays: 0,
    maxStorageMb: 0,
    bytes: 4096,
    recording: true,
    errorCode: '',
};
export function historyPage(entries: Activity[] = []): Contract.ActivityHistoryPage {
    return {
        entries: entries.filter((entry) => !['queued', 'generating'].includes(entry.status)),
        nextCursor: null,
        hasMore: false,
        total: entries.length,
        storage: historyStorage,
    };
}
