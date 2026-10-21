import type {Activity} from '@/shared/api/client';

export function isActive(entry: Activity): boolean {
    return entry.status === 'queued' || entry.status === 'generating';
}

export function activityTime(entry: Activity): number {
    return Math.max(entry.date * 1000, entry.startedAt ?? 0, entry.finishedAt ?? 0);
}

export function mergeActivityEntries(
    previous: Activity[],
    incoming: Activity[],
    position: 'newer' | 'older' = 'newer',
): Activity[] {
    const saved = new Map(previous.map((entry) => [entry.id, entry]));

    incoming.forEach((entry) => {
        const before = saved.get(entry.id);
        const stale =
            before && ((!isActive(before) && isActive(entry)) || activityTime(before) > activityTime(entry));
        saved.set(entry.id, stale ? before : entry);
    });
    const result = new Map<string, Activity>();
    const ordered = position === 'older' ? [...previous, ...incoming] : [...incoming, ...previous];
    ordered.forEach((entry) => {
        if (!result.has(entry.id)) {
            result.set(entry.id, saved.get(entry.id) ?? entry);
        }
    });

    return [...result.values()];
}

export function orderActivityEntries(entries: Activity[]): Activity[] {
    const rank = (entry: Activity) => (entry.status === 'generating' ? 0 : entry.status === 'queued' ? 1 : 2);
    return entries.toSorted((first, second) => {
        const position = (entry: Activity) =>
            entry.queuePosition && entry.queuePosition > 0 ? entry.queuePosition : Number.MAX_SAFE_INTEGER;
        return (
            rank(first) - rank(second) ||
            (first.status === 'queued' && second.status === 'queued'
                ? position(first) - position(second)
                : 0) ||
            activityTime(second) - activityTime(first)
        );
    });
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
