import {useCallback, useEffect, useRef, useState} from 'react';
import type {MemoryDocument} from '@aigram/api-client';
import {api, errorMessage} from '@/shared/api/client';

const POLL_INTERVAL_MS = 5000;
const MEMORY_UPDATED_EVENT = 'memory-updated';

type ReadSnapshot = {readonly chatId: string; readonly document?: MemoryDocument; readonly error: string};
type MemoryMutation = () => Promise<MemoryDocument | void>;
type MemoryReader = {
    readonly chatId: string;
    readonly read: (force?: boolean) => Promise<MemoryDocument | undefined>;
    readonly mutate: (action: MemoryMutation) => Promise<void>;
    readonly visibilityChanged: () => void;
};

export function useMemoryDocumentRead(chatId: string, enabled: boolean) {
    const [snapshot, setSnapshot] = useState<ReadSnapshot>({chatId, error: ''});
    const reader = useRef<MemoryReader | null>(null);
    const enabledRef = useRef(enabled);

    useEffect(() => {
        enabledRef.current = enabled;
        reader.current?.visibilityChanged();
    }, [enabled]);

    useEffect(() => {
        let active = true;
        let generation = 0;
        let revision = 0;
        let completedRevision = -1;
        let mutating = false;
        let broadcasting = false;
        let documentValue: MemoryDocument | undefined;
        let fingerprint = '';
        let pending: {controller: AbortController; result: Promise<MemoryDocument | undefined>} | undefined;
        let timer: ReturnType<typeof setTimeout> | undefined;

        const readable = () =>
            active && enabledRef.current && document.visibilityState !== 'hidden' && !mutating;

        function publish(value: MemoryDocument | undefined, error = '') {
            if (value) {
                const nextFingerprint = JSON.stringify(value);
                if (nextFingerprint !== fingerprint) {
                    documentValue = value;
                    fingerprint = nextFingerprint;
                }
            }
            setSnapshot((previous) =>
                previous.chatId === chatId && previous.document === documentValue && previous.error === error
                    ? previous
                    : {chatId, document: documentValue, error},
            );
        }

        function stop() {
            generation += 1;
            clearTimeout(timer);
            timer = undefined;
            const previous = pending;
            pending = undefined;
            previous?.controller.abort();
        }

        function schedule() {
            clearTimeout(timer);
            timer = readable() ? setTimeout(() => void read(), POLL_INTERVAL_MS) : undefined;
        }

        function read(force = false): Promise<MemoryDocument | undefined> {
            if (force) {
                revision += 1;
            }
            if (!readable()) {
                return Promise.resolve(undefined);
            }
            clearTimeout(timer);
            timer = undefined;
            const version = generation;
            const requiredRevision = revision;
            const fresh = (result: Promise<MemoryDocument | undefined>) =>
                result.then((value) => {
                    if (!readable() || version !== generation) {
                        return undefined;
                    }
                    if (completedRevision < requiredRevision) {
                        return read();
                    }
                    return value;
                });
            if (pending) {
                return fresh(pending.result);
            }

            const controller = new AbortController();
            const current = () =>
                readable() &&
                version === generation &&
                pending?.controller === controller &&
                !controller.signal.aborted;
            const result = Promise.resolve()
                .then(async () => {
                    if (!current() || requiredRevision !== revision) {
                        return undefined;
                    }
                    try {
                        const value = chatId
                            ? await api.memory.getChatMemory({chatId}, {signal: controller.signal})
                            : await api.memory.getGlobalMemory({signal: controller.signal});
                        if (current() && requiredRevision === revision) {
                            completedRevision = requiredRevision;
                            publish(value);
                            return value;
                        }
                    } catch (cause) {
                        if (current() && requiredRevision === revision) {
                            completedRevision = requiredRevision;
                            publish(undefined, errorMessage(cause));
                        }
                    }
                    return undefined;
                })
                .finally(() => {
                    if (pending?.controller === controller) {
                        pending = undefined;
                        schedule();
                    }
                });
            pending = {controller, result};
            return fresh(result);
        }

        async function mutate(action: MemoryMutation) {
            stop();
            mutating = true;
            try {
                const value = await action();
                if (!active) {
                    return;
                }
                revision += 1;
                if (value) {
                    completedRevision = revision;
                    publish(value);
                }
                mutating = false;
                if (!value) {
                    await read(true);
                }
                if (!active) {
                    return;
                }
                broadcasting = true;
                try {
                    window.dispatchEvent(new Event(MEMORY_UPDATED_EVENT));
                } finally {
                    broadcasting = false;
                }
            } finally {
                mutating = false;
                schedule();
            }
        }

        function visibilityChanged() {
            if (readable()) {
                void read();
            } else {
                stop();
            }
        }

        const currentReader = {chatId, read, mutate, visibilityChanged};
        reader.current = currentReader;
        const updated = () => {
            if (!broadcasting) {
                void read(true);
            }
        };
        document.addEventListener('visibilitychange', visibilityChanged);
        window.addEventListener(MEMORY_UPDATED_EVENT, updated);
        void read();
        return () => {
            active = false;
            stop();
            if (reader.current === currentReader) {
                reader.current = null;
            }
            document.removeEventListener('visibilitychange', visibilityChanged);
            window.removeEventListener(MEMORY_UPDATED_EVENT, updated);
        };
    }, [chatId]);

    const reload = useCallback(
        () => (reader.current?.chatId === chatId ? reader.current.read(true) : Promise.resolve(undefined)),
        [chatId],
    );
    const mutate = useCallback(
        (action: MemoryMutation) =>
            reader.current?.chatId === chatId ? reader.current.mutate(action) : Promise.resolve(),
        [chatId],
    );

    return {
        document: snapshot.chatId === chatId ? snapshot.document : undefined,
        error: snapshot.chatId === chatId ? snapshot.error : '',
        reload,
        mutate,
    };
}
