'use client';

import {useCallback, useEffect, useRef, useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

type MemoryDocument = components['schemas']['MemoryDocument'];
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
            dataOrThrow(
                chatId
                    ? await api.GET('/api/memory/{chatId}', {params: {path: {chatId}}})
                    : await api.GET('/api/memory'),
            ),
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
            const value = dataOrThrow(
                chatId
                    ? await api.PATCH('/api/memory/{chatId}', {params: {path: {chatId}}, body})
                    : await api.PATCH('/api/memory', {body}),
            );
            setDocument(value);
            setNotes(value.notes);
            revision.current = value.revision;
        } catch (cause) {
            setError(errorMessage(cause));
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
                    dataOrThrow(
                        await api.POST('/api/memory/{chatId}', {
                            params: {path: {chatId}},
                            body: {rebuild, immediate},
                        }),
                    ),
                );
            } else {
                dataOrThrow(await api.POST('/api/memory/refresh', {body: {rebuild, immediate}}));
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
            const value = dataOrThrow(
                chatId
                    ? await api.POST('/api/memory/{chatId}/cancel', {params: {path: {chatId}}})
                    : await api.POST('/api/memory/cancel'),
            );
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
