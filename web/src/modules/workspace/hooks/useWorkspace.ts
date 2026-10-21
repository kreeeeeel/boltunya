import {useCallback, useEffect, useState} from 'react';

import {mergeRecentMessages} from '@/modules/workspace/model/mergeMessages';
import {shareSnapshot} from '@/modules/workspace/model/shareSnapshot';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {AppState, Message} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';

export function useWorkspace(chatId: string | null) {
    const [state, setState] = useState<AppState | null>(null);
    const [messages, setMessages] = useState<Message[]>([]);
    const [error, setError] = useState('');
    const [historyStatus, setHistoryStatus] = useState({chatId, resolved: false, error: ''});
    if (historyStatus.chatId !== chatId) {
        setHistoryStatus({chatId, resolved: false, error: ''});
    }

    const refresh = useCallback(async () => {
        try {
            const result: AppState = dataOrThrow(await api.GET('/api/state'));
            const desktop = desktopBridge();
            if (desktop) {
                result.window = await desktop.state();
            }
            setState((previous) => shareSnapshot(previous, result));
            setError('');
        } catch (cause) {
            setError(errorMessage(cause));
        }
    }, []);

    useEffect(
        () =>
            desktopBridge()?.onState((windowState) => {
                setState((previous) => (previous ? {...previous, window: windowState} : previous));
            }),
        [],
    );

    useEffect(() => {
        let active = true;
        let timer: ReturnType<typeof setTimeout>;
        async function poll() {
            await refresh();
            if (active) {
                timer = setTimeout(poll, 2500);
            }
        }
        void poll();
        return () => {
            active = false;
            clearTimeout(timer);
        };
    }, [refresh]);

    useEffect(() => {
        if (!chatId) {
            return;
        }
        let active = true;
        let timer: ReturnType<typeof setTimeout>;
        async function pollHistory() {
            try {
                const result = dataOrThrow(
                    await api.GET('/api/chats/{chatId}/messages', {params: {path: {chatId: chatId ?? ''}}}),
                );
                if (active) {
                    setMessages((previous) => shareSnapshot(previous, mergeRecentMessages(previous, result)));
                    setHistoryStatus({chatId, resolved: true, error: ''});
                }
            } catch (cause) {
                if (active) {
                    setHistoryStatus({chatId, resolved: true, error: errorMessage(cause)});
                }
            } finally {
                if (active) {
                    timer = setTimeout(pollHistory, 2500);
                }
            }
        }
        void pollHistory();
        return () => {
            active = false;
            clearTimeout(timer);
        };
    }, [chatId]);

    return {
        state,
        messages,
        error,
        historyError: historyStatus.error,
        stateLoading: state === null && !error,
        historyLoading: chatId !== null && !historyStatus.resolved,
        refresh,
        setMessages,
    };
}
