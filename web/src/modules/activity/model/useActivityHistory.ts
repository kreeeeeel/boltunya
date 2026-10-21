import {useCallback, useEffect, useRef, useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Activity} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

const PAGE_SIZE = 30;
const POLL_INTERVAL_MS = 5000;

function mergeEntries(previous: Activity[], incoming: Activity[]) {
    return [...new Map([...previous, ...incoming].map((entry) => [entry.id, entry])).values()];
}

export function useActivityHistory() {
    const [entries, setEntries] = useState<Activity[]>([]);
    const [storage, setStorage] = useState<components['schemas']['ActivityHistoryStorage']>();
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState('');
    const [hasMore, setHasMore] = useState(false);
    const [total, setTotal] = useState(0);
    const cursor = useRef<string | null>(null);
    const paginated = useRef(false);
    const generation = useRef(0);
    const requests = useRef(new Set<AbortController>());
    const load = useCallback(async (more: boolean, reset = false) => {
        if (reset) {
            generation.current += 1;
            paginated.current = false;
            setLoadingMore(false);
        }
        const version = generation.current;
        const controller = new AbortController();
        requests.current.add(controller);
        if (more) {
            setLoadingMore(true);
        }
        try {
            const page = dataOrThrow(
                await api.GET('/api/activity/history', {
                    params: {
                        query: {limit: PAGE_SIZE, cursor: more ? (cursor.current ?? undefined) : undefined},
                    },
                    signal: controller.signal,
                }),
            );
            if (controller.signal.aborted || version !== generation.current) {
                return;
            }
            setEntries((previous) => mergeEntries(reset ? [] : previous, page.entries));
            setStorage(page.storage);
            setTotal(page.total);
            if (more || !paginated.current) {
                cursor.current = page.nextCursor;
                setHasMore(page.hasMore);
            }
            if (more) {
                paginated.current = true;
            }
            setError('');
        } catch (cause) {
            if (!controller.signal.aborted && version === generation.current) {
                setError(errorMessage(cause));
            }
        } finally {
            requests.current.delete(controller);
            if (!controller.signal.aborted && version === generation.current) {
                setLoading(false);
                if (more) {
                    setLoadingMore(false);
                }
            }
        }
    }, []);
    useEffect(() => {
        let active = true;
        let timer: ReturnType<typeof setTimeout>;
        const pending = requests.current;
        async function poll() {
            await load(false);
            if (active) {
                timer = setTimeout(poll, POLL_INTERVAL_MS);
            }
        }
        void poll();
        return () => {
            active = false;
            clearTimeout(timer);
            pending.forEach((request) => request.abort());
        };
    }, [load]);
    return {
        entries,
        storage,
        loading,
        loadingMore,
        error,
        hasMore,
        total,
        loadMore: () => load(true),
        refresh: (reset = false) => load(false, reset),
    };
}
