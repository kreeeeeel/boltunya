import type {SpeechState} from '@aigram/api-client';
import {api, errorMessage} from '@/shared/api/client';

const UPDATE_EVENT = 'speech-assets-updated';
const POLL_INTERVAL = 1000;

type SpeechSnapshot = {readonly state: SpeechState | undefined; readonly error: string};

const INITIAL_SNAPSHOT: SpeechSnapshot = {state: undefined, error: ''};

export function createSpeechStateStore(readState: (signal: AbortSignal) => Promise<SpeechState>) {
    const listeners = new Set<() => void>();
    let snapshot = INITIAL_SNAPSHOT;
    let fingerprint = '';
    let revision = 0;
    let pending: AbortController | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;

    function visible() {
        return typeof document !== 'undefined' && document.visibilityState !== 'hidden';
    }

    function active() {
        return listeners.size > 0 && visible();
    }

    function notify() {
        listeners.forEach((listener) => listener());
    }

    function stop() {
        clearTimeout(timer);
        timer = undefined;
        const previous = pending;
        pending = undefined;
        previous?.abort();
    }

    async function read() {
        if (!active() || pending) {
            return;
        }

        const controller = new AbortController();
        const version = revision;
        pending = controller;
        let polling = false;

        try {
            const next = await readState(controller.signal);
            if (pending !== controller || controller.signal.aborted || version !== revision || !active()) {
                return;
            }

            const nextFingerprint = JSON.stringify(next);
            if (nextFingerprint !== fingerprint || snapshot.error) {
                snapshot = {state: nextFingerprint === fingerprint ? snapshot.state : next, error: ''};
                fingerprint = nextFingerprint;
                notify();
            }
            polling = next.downloads.some((download) =>
                ['queued', 'downloading', 'extracting'].includes(download.phase),
            );
        } catch (cause) {
            if (pending === controller && !controller.signal.aborted && version === revision && active()) {
                const error = errorMessage(cause);
                if (snapshot.error !== error) {
                    snapshot = {...snapshot, error};
                    notify();
                }
            }
        } finally {
            if (pending === controller) {
                pending = undefined;
                if (active()) {
                    if (version !== revision) {
                        void read();
                    } else if (polling) {
                        timer = setTimeout(() => {
                            timer = undefined;
                            void read();
                        }, POLL_INTERVAL);
                    }
                }
            }
        }
    }

    function refresh() {
        revision += 1;
        clearTimeout(timer);
        timer = undefined;
        void read();
    }

    function visibilityChanged() {
        if (visible()) {
            refresh();
        } else {
            stop();
        }
    }

    function subscribe(listener: () => void) {
        listeners.add(listener);
        if (listeners.size === 1) {
            document.addEventListener('visibilitychange', visibilityChanged);
            window.addEventListener(UPDATE_EVENT, refresh);
            refresh();
        } else if (!pending) {
            clearTimeout(timer);
            timer = undefined;
            void read();
        }

        return () => {
            listeners.delete(listener);
            if (!listeners.size) {
                stop();
                document.removeEventListener('visibilitychange', visibilityChanged);
                window.removeEventListener(UPDATE_EVENT, refresh);
            }
        };
    }

    return {
        subscribe,
        refresh,
        getSnapshot: () => snapshot,
        getServerSnapshot: () => INITIAL_SNAPSHOT,
    };
}

export const speechStateStore = createSpeechStateStore((signal) => api.speech.speechState({signal}));
