import {useCallback, useDeferredValue, useEffect, useMemo, useRef, useState} from 'react';

import type {Chat} from '@/shared/api/client';

const PAGE_SIZE = 40;
const LOAD_THRESHOLD_PX = 160;
const SEARCH_DELAY_MS = 250;

export function useChatPages(chats: Chat[], locale: string) {
    const [query, setQuery] = useState({search: '', filter: 'all'});
    const [search, setSearch] = useState('');
    const filtering = useMemo(() => ({search, filter: query.filter}), [search, query.filter]);
    const deferred = useDeferredValue(filtering);
    const [limit, setLimit] = useState(PAGE_SIZE);
    const [loadingMore, setLoadingMore] = useState(false);
    const viewport = useRef<HTMLDivElement>(null);
    const pendingFrame = useRef(0);
    const resetPage = useCallback(() => {
        cancelAnimationFrame(pendingFrame.current);
        pendingFrame.current = 0;
        setLoadingMore(false);
        setLimit(PAGE_SIZE);
        if (viewport.current) {
            viewport.current.scrollTop = 0;
        }
    }, []);

    useEffect(() => {
        if (query.search === search) {
            return;
        }
        const timer = setTimeout(() => {
            resetPage();
            setSearch(query.search);
        }, SEARCH_DELAY_MS);
        return () => clearTimeout(timer);
    }, [query.search, search, resetPage]);
    const visible = useMemo(() => {
        const search = deferred.search.trim().toLocaleLowerCase(locale);
        return chats.filter(
            (chat) =>
                chat.title.toLocaleLowerCase(locale).includes(search) &&
                (deferred.filter !== 'withAi' || chat.rule.enabled) &&
                (deferred.filter !== 'unread' || chat.unread > 0),
        );
    }, [chats, deferred, locale]);
    const changing = query.filter !== deferred.filter;
    const searching = query.search !== deferred.search;

    useEffect(() => () => cancelAnimationFrame(pendingFrame.current), []);

    function update(next: Partial<typeof query>) {
        if (next.filter !== undefined) {
            resetPage();
        }
        setQuery((previous) => ({...previous, ...next}));
    }

    function loadMore() {
        const element = viewport.current;
        if (
            !element ||
            loadingMore ||
            pendingFrame.current !== 0 ||
            changing ||
            searching ||
            limit >= visible.length ||
            element.scrollHeight - element.scrollTop - element.clientHeight > LOAD_THRESHOLD_PX
        ) {
            return;
        }
        setLoadingMore(true);
        pendingFrame.current = requestAnimationFrame(() => {
            pendingFrame.current = requestAnimationFrame(() => {
                pendingFrame.current = 0;
                setLimit((previous) => previous + PAGE_SIZE);
                setLoadingMore(false);
            });
        });
    }

    return {
        query,
        update,
        changing,
        searching,
        viewport,
        loadMore,
        loadingMore,
        page: visible.slice(0, limit),
        hasMore: limit < visible.length,
        empty: visible.length === 0,
    };
}
