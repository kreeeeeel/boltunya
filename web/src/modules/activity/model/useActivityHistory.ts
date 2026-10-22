import {useCallback, useEffect, useRef, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type {Activity} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import {mergeActivityEntries, orderActivityEntries} from '@/modules/activity/model/activity';

const PAGE_SIZE = 30;
const POLL_INTERVAL_MS = 5000;

type HistoryRequest = {readonly controller: AbortController; readonly result: Promise<void>};

export function useActivityHistory() {
    const [entries, setEntries] = useState<Activity[]>([]);
    const [storage, setStorage] = useState<Contract.ActivityHistoryStorage>();
    const [loading, setLoading] = useState(true);
    const [loadingMore, setLoadingMore] = useState(false);
    const [error, setError] = useState('');
    const [hasMore, setHasMore] = useState(false);
    const [total, setTotal] = useState(0);
    const cursor = useRef<string | null>(null);
    const paginated = useRef(false);
    const resetPending = useRef(false);
    const mounted = useRef(false);
    const generation = useRef(0);
    const revision = useRef(0);
    const completedRevision = useRef(-1);
    const acceptedHead = useRef(0);
    const headRequest = useRef<HistoryRequest | null>(null);
    const olderRequest = useRef<HistoryRequest | null>(null);
    const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    const abortReads = useCallback(() => {
        generation.current += 1;
        clearTimeout(timer.current);
        timer.current = undefined;
        headRequest.current?.controller.abort();
        olderRequest.current?.controller.abort();
        headRequest.current = null;
        olderRequest.current = null;
    }, []);

    const loadHead = useCallback(function readHead(force = false): Promise<void> {
        if (force) {
            revision.current += 1;
        }
        if (!mounted.current || document.visibilityState === 'hidden') {
            return Promise.resolve();
        }

        clearTimeout(timer.current);
        timer.current = undefined;
        const version = generation.current;
        const requiredRevision = revision.current;
        const fresh = (result: Promise<void>) =>
            result.then(() => {
                if (
                    mounted.current &&
                    document.visibilityState !== 'hidden' &&
                    version === generation.current &&
                    completedRevision.current < requiredRevision
                ) {
                    return readHead();
                }
            });

        if (headRequest.current) {
            return fresh(headRequest.current.result);
        }

        const controller = new AbortController();
        const current = () =>
            mounted.current &&
            document.visibilityState !== 'hidden' &&
            version === generation.current &&
            headRequest.current?.controller === controller &&
            !controller.signal.aborted;
        const result = Promise.resolve()
            .then(async () => {
                if (!current()) {
                    return;
                }
                try {
                    const page = await api.activity.activityHistory(
                        {limit: PAGE_SIZE},
                        {signal: controller.signal},
                    );
                    if (!current() || requiredRevision !== revision.current) {
                        return;
                    }

                    const reset = resetPending.current;
                    resetPending.current = false;
                    completedRevision.current = requiredRevision;
                    acceptedHead.current += 1;
                    setEntries((previous) =>
                        orderActivityEntries(mergeActivityEntries(reset ? [] : previous, page.entries)),
                    );
                    setStorage(page.storage);
                    setTotal(page.total);
                    if (!paginated.current) {
                        cursor.current = page.nextCursor;
                        setHasMore(page.hasMore);
                    }
                    setError('');
                } catch (cause) {
                    if (current() && requiredRevision === revision.current) {
                        completedRevision.current = requiredRevision;
                        setError(errorMessage(cause));
                    }
                }
            })
            .finally(() => {
                if (current()) {
                    headRequest.current = null;
                    if (completedRevision.current >= revision.current) {
                        setLoading(false);
                    }
                    timer.current = setTimeout(() => void readHead(), POLL_INTERVAL_MS);
                }
            });
        headRequest.current = {controller, result};
        return fresh(result);
    }, []);

    const loadMore = useCallback((): Promise<void> => {
        if (olderRequest.current) {
            return olderRequest.current.result;
        }
        if (
            !mounted.current ||
            document.visibilityState === 'hidden' ||
            resetPending.current ||
            !cursor.current
        ) {
            return Promise.resolve();
        }

        const version = generation.current;
        const head = acceptedHead.current;
        const pageCursor = cursor.current;
        const controller = new AbortController();
        const current = () =>
            mounted.current &&
            document.visibilityState !== 'hidden' &&
            version === generation.current &&
            olderRequest.current?.controller === controller &&
            !controller.signal.aborted;
        setLoadingMore(true);
        const result = Promise.resolve()
            .then(async () => {
                if (!current()) {
                    return;
                }
                try {
                    const page = await api.activity.activityHistory(
                        {limit: PAGE_SIZE, cursor: pageCursor},
                        {signal: controller.signal},
                    );
                    if (!current()) {
                        return;
                    }

                    const headChanged = head !== acceptedHead.current;
                    setEntries((previous) => {
                        const existing = new Set(previous.map((entry) => entry.id));
                        const incoming = headChanged
                            ? page.entries.filter((entry) => !existing.has(entry.id))
                            : page.entries;
                        return orderActivityEntries(mergeActivityEntries(previous, incoming, 'older'));
                    });
                    if (!headChanged) {
                        setStorage(page.storage);
                        setTotal(page.total);
                    }
                    paginated.current = true;
                    cursor.current = page.nextCursor;
                    setHasMore(page.hasMore);
                    setError('');
                } catch (cause) {
                    if (current()) {
                        setError(errorMessage(cause));
                    }
                }
            })
            .finally(() => {
                if (current()) {
                    olderRequest.current = null;
                    setLoadingMore(false);
                }
            });
        olderRequest.current = {controller, result};
        return result;
    }, []);

    const refresh = useCallback(
        (reset = false) => {
            if (reset) {
                abortReads();
                resetPending.current = true;
                paginated.current = false;
                cursor.current = null;
                setHasMore(false);
                setLoadingMore(false);
            }
            return loadHead(true);
        },
        [abortReads, loadHead],
    );

    useEffect(() => {
        mounted.current = true;
        const visibilityChanged = () => {
            if (document.visibilityState === 'hidden') {
                abortReads();
                setLoadingMore(false);
            } else {
                void loadHead();
            }
        };
        document.addEventListener('visibilitychange', visibilityChanged);
        void loadHead();
        return () => {
            mounted.current = false;
            abortReads();
            document.removeEventListener('visibilitychange', visibilityChanged);
        };
    }, [abortReads, loadHead]);

    return {entries, storage, loading, loadingMore, error, hasMore, total, loadMore, refresh};
}
