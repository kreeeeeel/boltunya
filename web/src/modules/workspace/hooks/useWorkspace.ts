import {useCallback, useEffect, useRef, useState} from 'react';
import {mergeRecentMessages} from '@/modules/workspace/model/mergeMessages';
import {shareSnapshot} from '@/modules/workspace/model/shareSnapshot';
import {api, errorMessage} from '@/shared/api/client';
import type {AppState, Message} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {applyUpdatedMessage} from '@/modules/workspace/model/applyUpdatedMessage';
export function useWorkspace(chatId: string | null, historyEnabled = true) {
    const [state, setState] = useState<AppState | null>(null);
    const [visible, setVisible] = useState(
        () => typeof document === 'undefined' || document.visibilityState !== 'hidden',
    );
    const stateRequest = useRef<{controller: AbortController; result: Promise<void>} | null>(null);
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
    const readState = useCallback((replace: boolean): Promise<void> => {
        const previous = stateRequest.current;
        if (previous && !replace && !previous.controller.signal.aborted) {
            return previous.result;
        }
        previous?.controller.abort();
        const controller = new AbortController();
        async function read() {
            try {
                const result: AppState = await api.system.state({signal: controller.signal});
                if (controller.signal.aborted) {
                    return;
                }
                const desktop = desktopBridge();
                if (desktop) {
                    result.window = await desktop.state();
                }
                if (!controller.signal.aborted) {
                    setState((current) => shareSnapshot(current, result));
                    setError('');
                }
            } catch (cause) {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            } finally {
                if (stateRequest.current?.controller === controller) {
                    stateRequest.current = null;
                }
            }
        }
        const result = read();
        stateRequest.current = {controller, result};
        return result;
    }, []);
    const refresh = useCallback(() => readState(true), [readState]);
    useEffect(() => {
        const update = () => setVisible(document.visibilityState !== 'hidden');
        document.addEventListener('visibilitychange', update);
        return () => document.removeEventListener('visibilitychange', update);
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
        let timer: ReturnType<typeof setTimeout> | undefined;
        async function poll() {
            await readState(false);
            if (active) {
                timer = setTimeout(poll, 2500);
            }
        }
        if (visible) {
            void poll();
        } else {
            stateRequest.current?.controller.abort();
        }
        return () => {
            active = false;
            clearTimeout(timer);
            stateRequest.current?.controller.abort();
        };
    }, [readState, visible]);
    useEffect(() => {
        if (!chatId || !historyEnabled || !visible) {
            return;
        }
        let active = true;
        const controller = new AbortController();
        let timer: ReturnType<typeof setTimeout>;
        async function pollHistory() {
            const version = mutation.current;
            try {
                const result = await api.chats.history({chatId: chatId ?? ''}, {signal: controller.signal});
                if (active && version === mutation.current) {
                    setMessages((previous) =>
                        version === mutation.current
                            ? shareSnapshot(previous, mergeRecentMessages(previous, result))
                            : previous,
                    );
                    setHistoryStatus((previous) =>
                        previous.chatId === chatId && previous.resolved && !previous.error
                            ? previous
                            : {chatId, resolved: true, error: ''},
                    );
                }
            } catch (cause) {
                if (active && version === mutation.current) {
                    const message = errorMessage(cause);
                    setHistoryStatus((previous) =>
                        previous.chatId === chatId && previous.resolved && previous.error === message
                            ? previous
                            : {chatId, resolved: true, error: message},
                    );
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
            controller.abort();
            clearTimeout(timer);
        };
    }, [chatId, historyEnabled, visible]);
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
