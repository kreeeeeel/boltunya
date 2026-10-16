import type {Activity} from '@/shared/api/client';

export function isActive(entry: Activity): boolean {
    return entry.status === 'queued' || entry.status === 'generating';
}

export function statusTheme(status: string): 'success' | 'danger' | 'info' | 'normal' {
    if (status === 'sent') {
        return 'success';
    }
    if (status === 'error') {
        return 'danger';
    }
    return status === 'cancelled' ? 'normal' : 'info';
}

export function seconds(ms: number, locale: string): string {
    return new Intl.NumberFormat(locale, {maximumFractionDigits: 1}).format(ms / 1000);
}
