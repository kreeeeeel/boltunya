import type {Activity} from '@/shared/api/client';

export function isActive(entry: Activity): boolean {
    return entry.status === 'queued' || entry.status === 'generating';
}

export function statusLabelKey(entry: Pick<Activity, 'status' | 'kind'>): string {
    if (entry.status === 'interrupted') {
        return 'phase.interrupted';
    }
    if (entry.status === 'ready') {
        if (entry.kind === 'memory') {
            return 'memoryReady';
        }
        if (entry.kind === 'draft') {
            return 'draftReady';
        }
    }
    return 'status.' + entry.status;
}

export function statusTheme(status: string): 'success' | 'danger' | 'info' | 'normal' {
    if (status === 'sent' || status === 'ready') {
        return 'success';
    }
    if (status === 'error') {
        return 'danger';
    }
    return status === 'cancelled' || status === 'interrupted' ? 'normal' : 'info';
}

export function seconds(ms: number, locale: string): string {
    return new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(ms / 1000);
}
