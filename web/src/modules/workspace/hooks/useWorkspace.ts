import {useCallback, useEffect, useRef, useState} from 'react';
import {mergeRecentMessages} from '@/modules/workspace/model/mergeMessages';
import {shareSnapshot} from '@/modules/workspace/model/shareSnapshot';
import {api, errorMessage} from '@/shared/api/client';
import type {AppState, Message} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {applyUpdatedMessage} from '@/modules/workspace/model/applyUpdatedMessage';
export function useWorkspace(chatId: string | null) {
    const [state, setState] = useState<AppState | null>(null);
    const [messages, setMessages] = useState<Message[]>([]);
    const mutation = useRef(0);
    const historyVersion = useCallback(() => mutation.current, []);
    const updateMessage = useCallback((updated: Message) => {
        mutation.current += 1;
        setMessages((previous) => applyUpdatedMessage(previous, updated));
    }, []);
    const [error, setError] = useState('');
    const [historyStatus, setHistoryStatus] = useState({chatId, resolved: false, error: ''});
    if (historyStatus.chatId !== chatId) {
        setHistoryStatus({chatId, resolved: false, error: ''});
    }
    const refresh = useCallback(async () => {
        try {
            const result: AppState = await api.system.state();
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
            const version = mutation.current;
            try {
                const result = await api.chats.history({
                    chatId: chatId ?? '',
                });
                if (active && version === mutation.current) {
                    setMessages((previous) =>
                        version === mutation.current
                            ? shareSnapshot(previous, mergeRecentMessages(previous, result))
                            : previous,
                    );
                    setHistoryStatus({chatId, resolved: true, error: ''});
                }
            } catch (cause) {
                if (active && version === mutation.current) {
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
        updateMessage,
        historyVersion,
    };
}
