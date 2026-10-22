import type {ModelProfileReport} from '@aigram/api-client';
import {api, errorMessage} from '@/shared/api/client';

const UPDATE_EVENT = 'aigram-model-profiles-changed';

type ModelProfilesSnapshot = ModelProfileReport & {readonly error: string; readonly loading: boolean};

const INITIAL_SNAPSHOT: ModelProfilesSnapshot = {profiles: [], presets: [], error: '', loading: true};

export function createModelProfilesStore(readReport: (signal: AbortSignal) => Promise<ModelProfileReport>) {
    const listeners = new Set<() => void>();
    let snapshot = INITIAL_SNAPSHOT;
    let fingerprint = '';
    let generation = 0;
    let revision = 0;
    let completedRevision = -1;
    let pending: {readonly controller: AbortController; readonly result: Promise<void>} | undefined;

    function active() {
        return listeners.size > 0 && document.visibilityState !== 'hidden';
    }

    function notify() {
        listeners.forEach((listener) => listener());
    }

    function stop() {
        generation += 1;
        const previous = pending;
        pending = undefined;
        previous?.controller.abort();
    }

    function read(force = false): Promise<void> {
        if (force) {
            revision += 1;
        }
        if (!active()) {
            return Promise.resolve();
        }

        const version = generation;
        const requiredRevision = revision;
        const fresh = (result: Promise<void>) =>
            result.then(() => {
                if (active() && version === generation && completedRevision < requiredRevision) {
                    return read();
                }
            });
        if (pending) {
            return fresh(pending.result);
        }

        const controller = new AbortController();
        const current = () =>
            active() &&
            version === generation &&
            pending?.controller === controller &&
            !controller.signal.aborted;
        const result = Promise.resolve()
            .then(async () => {
                if (!current() || requiredRevision !== revision) {
                    return;
                }
                try {
                    const report = await readReport(controller.signal);
                    if (!current() || requiredRevision !== revision) {
                        return;
                    }

                    completedRevision = requiredRevision;
                    const nextFingerprint = JSON.stringify(report);
                    if (nextFingerprint !== fingerprint || snapshot.loading || snapshot.error) {
                        const unchanged = nextFingerprint === fingerprint;
                        snapshot = {
                            profiles: unchanged ? snapshot.profiles : report.profiles,
                            presets: unchanged ? snapshot.presets : report.presets,
                            error: '',
                            loading: false,
                        };
                        fingerprint = nextFingerprint;
                        notify();
                    }
                } catch (cause) {
                    if (current() && requiredRevision === revision) {
                        completedRevision = requiredRevision;
                        const error = errorMessage(cause);
                        if (snapshot.loading || snapshot.error !== error) {
                            snapshot = {...snapshot, loading: false, error};
                            notify();
                        }
                    }
                }
            })
            .finally(() => {
                if (pending?.controller === controller) {
                    pending = undefined;
                }
            });
        pending = {controller, result};
        return fresh(result);
    }

    function refresh() {
        return read(true);
    }

    function changed() {
        void refresh();
    }

    function visibilityChanged() {
        if (document.visibilityState === 'hidden') {
            stop();
        } else {
            void read();
        }
    }

    function subscribe(listener: () => void) {
        listeners.add(listener);
        if (listeners.size === 1) {
            document.addEventListener('visibilitychange', visibilityChanged);
            window.addEventListener(UPDATE_EVENT, changed);
        }
        void read();

        return () => {
            listeners.delete(listener);
            if (!listeners.size) {
                stop();
                document.removeEventListener('visibilitychange', visibilityChanged);
                window.removeEventListener(UPDATE_EVENT, changed);
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

export const modelProfilesStore = createModelProfilesStore((signal) => api.models.modelProfiles({signal}));
