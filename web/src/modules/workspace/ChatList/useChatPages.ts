import {useDeferredValue, useEffect, useMemo, useRef, useState} from 'react';

import type {Chat} from '@/shared/api/client';

const PAGE_SIZE = 40;
const LOAD_THRESHOLD_PX = 160;

export function useChatPages(chats: Chat[], locale: string) {
    const [query, setQuery] = useState({search: '', filter: 'all'});
    const deferred = useDeferredValue(query);
    const [limit, setLimit] = useState(PAGE_SIZE);
    const [loadingMore, setLoadingMore] = useState(false);
    const viewport = useRef<HTMLDivElement>(null);
    const pendingFrame = useRef(0);
    const visible = useMemo(() => {
        const search = deferred.search.trim().toLocaleLowerCase(locale);
        return chats.filter(
            (chat) =>
                chat.title.toLocaleLowerCase(locale).includes(search) &&
                (deferred.filter !== 'withAi' || chat.rule.enabled) &&
                (deferred.filter !== 'unread' || chat.unread > 0),
        );
    }, [chats, deferred, locale]);
    const changing = query !== deferred;

    useEffect(() => () => cancelAnimationFrame(pendingFrame.current), []);

    function update(next: Partial<typeof query>) {
        cancelAnimationFrame(pendingFrame.current);
        pendingFrame.current = 0;
        setLoadingMore(false);
        setLimit(PAGE_SIZE);
        setQuery((previous) => ({...previous, ...next}));
        if (viewport.current) {
            viewport.current.scrollTop = 0;
        }
    }

    function loadMore() {
        const element = viewport.current;
        if (
            !element ||
            loadingMore ||
            pendingFrame.current !== 0 ||
            changing ||
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
        viewport,
        loadMore,
        loadingMore,
        page: visible.slice(0, limit),
        hasMore: limit < visible.length,
        empty: visible.length === 0,
    };
}
