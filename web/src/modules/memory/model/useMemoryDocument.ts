'use client';
import {useCallback, useEffect, useRef, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import {useMemoryDocumentRead} from '@/modules/memory/model/useMemoryDocumentRead';

type NotesDraft = {
    readonly chatId: string;
    readonly identity: symbol;
    readonly notes: string;
    readonly revision: string;
    readonly initialized: boolean;
    readonly version: number;
};

function emptyDraft(chatId: string): NotesDraft {
    return {chatId, identity: Symbol(), notes: '', revision: '', initialized: false, version: 0};
}

export function useMemoryDocument(chatId: string, active = true) {
    const memory = useMemoryDocumentRead(chatId, active);
    const [draft, setDraft] = useState(() => emptyDraft(chatId));
    const [action, setAction] = useState({identity: draft.identity, error: '', busy: false});
    const mounted = useRef<symbol | null>(null);
    const pendingAction = useRef<symbol | null>(null);
    if (draft.chatId !== chatId) {
        setDraft(emptyDraft(chatId));
    } else if (!draft.initialized && memory.document) {
        setDraft({
            ...draft,
            notes: draft.version ? draft.notes : memory.document.notes,
            revision: memory.document.revision,
            initialized: true,
        });
    }
    const identity = draft.identity;

    useEffect(() => {
        mounted.current = identity;
        return () => {
            if (mounted.current === identity) {
                mounted.current = null;
            }
        };
    }, [identity]);

    const setNotes = useCallback(
        (notes: string) => {
            setDraft((previous) =>
                previous.identity === identity
                    ? {...previous, notes, version: previous.version + 1}
                    : previous,
            );
        },
        [identity],
    );

    const current = () => mounted.current === identity;
    const start = () => {
        if (!current() || pendingAction.current === identity) {
            return false;
        }
        pendingAction.current = identity;
        setAction({identity, error: '', busy: true});
        return true;
    };
    const finish = () => {
        if (pendingAction.current === identity) {
            pendingAction.current = null;
        }
        if (current()) {
            setAction((previous) => (previous.identity === identity ? {...previous, busy: false} : previous));
        }
    };
    const failed = (cause: unknown) => {
        if (current()) {
            setAction({identity, error: errorMessage(cause), busy: false});
        }
    };

    async function reload() {
        if (!start()) {
            return;
        }
        try {
            const value = await memory.reload();
            if (value && current()) {
                setDraft((previous) =>
                    previous.identity === identity
                        ? {
                              ...previous,
                              notes: previous.version === draft.version ? value.notes : previous.notes,
                              revision: value.revision,
                              initialized: true,
                          }
                        : previous,
                );
            }
        } finally {
            finish();
        }
    }

    async function save() {
        if (!start()) {
            return false;
        }
        try {
            const body = {notes: draft.notes, revision: draft.revision};
            await memory.mutate(async () => {
                const value = chatId
                    ? await api.memory.updateChatMemory({chatId, memoryNotesUpdate: body})
                    : await api.memory.updateGlobalMemory({memoryNotesUpdate: body});
                if (current()) {
                    setDraft((previous) =>
                        previous.identity === identity
                            ? {
                                  ...previous,
                                  notes: previous.version === draft.version ? value.notes : previous.notes,
                                  revision: value.revision,
                                  initialized: true,
                              }
                            : previous,
                    );
                }
                return value;
            });
            return current();
        } catch (cause) {
            failed(cause);
            return false;
        } finally {
            finish();
        }
    }

    async function refresh(rebuild = false, immediate = false): Promise<boolean> {
        if (!start()) {
            return false;
        }
        try {
            await memory.mutate(async () =>
                chatId
                    ? await api.memory.refreshChatMemory({chatId, memoryRefreshRequest: {rebuild, immediate}})
                    : await api.memory
                          .refreshChangedMemory({memoryRefreshRequest: {rebuild, immediate}})
                          .then(() => undefined),
            );
            return current();
        } catch (cause) {
            failed(cause);
            return false;
        } finally {
            finish();
        }
    }

    async function cancel() {
        if (!start()) {
            return;
        }
        try {
            await memory.mutate(async () =>
                chatId
                    ? await api.memory.cancelChatMemoryCollection({chatId})
                    : await api.memory.cancelGlobalMemoryCollection(),
            );
        } catch (cause) {
            failed(cause);
        } finally {
            finish();
        }
    }

    return {
        document: memory.document,
        notes: draft.chatId === chatId ? draft.notes : '',
        setNotes,
        error: (draft.chatId === chatId && action.identity === identity ? action.error : '') || memory.error,
        busy: draft.chatId === chatId && action.identity === identity && action.busy,
        reload,
        save,
        refresh,
        cancel,
    };
}
