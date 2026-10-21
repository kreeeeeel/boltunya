'use client';
import {useCallback, useEffect, useRef, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
type MemoryDocument = Contract.MemoryDocument;
const POLL_INTERVAL_MS = 5000;
const MEMORY_UPDATED_EVENT = 'memory-updated';
export function useMemoryDocument(chatId: string) {
    const [document, setDocument] = useState<MemoryDocument>();
    const [notes, setNotes] = useState('');
    const [error, setError] = useState('');
    const [pollError, setPollError] = useState('');
    const [busy, setBusy] = useState(false);
    const revision = useRef('');
    const initialized = useRef(false);
    const read = useCallback(
        async () =>
            chatId
                ? await api.memory.getChatMemory({
                      chatId,
                  })
                : await api.memory.getGlobalMemory(),
        [chatId],
    );
    useEffect(() => {
        let active = true;
        let timer: ReturnType<typeof setTimeout>;
        let polling = false;
        let repeat = false;
        async function poll() {
            if (polling) {
                repeat = true;
                return;
            }
            polling = true;
            try {
                const value = await read();
                if (active) {
                    setDocument(value);
                    setPollError('');
                    if (!initialized.current) {
                        setNotes(value.notes);
                        revision.current = value.revision;
                        initialized.current = true;
                    }
                }
            } catch (cause) {
                if (active) {
                    setPollError(errorMessage(cause));
                }
            } finally {
                polling = false;
                if (active) {
                    if (repeat) {
                        repeat = false;
                        void poll();
                    } else {
                        timer = setTimeout(poll, POLL_INTERVAL_MS);
                    }
                }
            }
        }
        void poll();
        const updated = () => {
            clearTimeout(timer);
            void poll();
        };
        window.addEventListener(MEMORY_UPDATED_EVENT, updated);
        return () => {
            active = false;
            clearTimeout(timer);
            window.removeEventListener(MEMORY_UPDATED_EVENT, updated);
        };
    }, [read]);
    async function reload() {
        setBusy(true);
        try {
            const value = await read();
            setDocument(value);
            setNotes(value.notes);
            revision.current = value.revision;
            initialized.current = true;
            setError('');
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    async function save() {
        setBusy(true);
        setError('');
        try {
            const body = {notes, revision: revision.current};
            const value = chatId
                ? await api.memory.updateChatMemory({
                      chatId,
                      memoryNotesUpdate: body,
                  })
                : await api.memory.updateGlobalMemory({
                      memoryNotesUpdate: body,
                  });
            setDocument(value);
            setNotes(value.notes);
            revision.current = value.revision;
            return true;
        } catch (cause) {
            setError(errorMessage(cause));
            return false;
        } finally {
            setBusy(false);
        }
    }
    async function refresh(rebuild = false, immediate = false): Promise<boolean> {
        setBusy(true);
        setError('');
        try {
            if (chatId) {
                setDocument(
                    await api.memory.refreshChatMemory({
                        chatId,
                        memoryRefreshRequest: {rebuild, immediate},
                    }),
                );
            } else {
                await api.memory.refreshChangedMemory({
                    memoryRefreshRequest: {rebuild, immediate},
                });
                setDocument(await read());
            }
            window.dispatchEvent(new Event(MEMORY_UPDATED_EVENT));
            return true;
        } catch (cause) {
            setError(errorMessage(cause));
            return false;
        } finally {
            setBusy(false);
        }
    }
    async function cancel() {
        setBusy(true);
        setError('');
        try {
            const value = chatId
                ? await api.memory.cancelChatMemoryCollection({
                      chatId,
                  })
                : await api.memory.cancelGlobalMemoryCollection();
            setDocument(value);
            window.dispatchEvent(new Event(MEMORY_UPDATED_EVENT));
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return {document, notes, setNotes, error: error || pollError, busy, reload, save, refresh, cancel};
}
