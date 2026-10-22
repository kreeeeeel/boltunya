import {useEffect, useRef, useState} from 'react';
import {isActive} from '@/modules/activity/model/activity';
import {api, errorMessage} from '@/shared/api/client';
import type {ActivityDetails} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';

const POLL_INTERVAL_MS = 1000;

export function useActivityDetails(id: string) {
    const [snapshot, setSnapshot] = useState<{id: string; details: ActivityDetails | null; error: string}>({
        id,
        details: null,
        error: '',
    });
    const reader = useRef<{id: string; refresh: () => void} | null>(null);

    useEffect(() => {
        let active = true;
        let generation = 0;
        let revision = 0;
        let completedRevision = -1;
        let pending: {controller: AbortController; result: Promise<void>} | undefined;
        let timer: ReturnType<typeof setTimeout> | undefined;

        function stop() {
            generation += 1;
            clearTimeout(timer);
            timer = undefined;
            pending?.controller.abort();
            pending = undefined;
        }

        function read(force = false): Promise<void> {
            if (force) {
                revision += 1;
            }
            if (!active || document.visibilityState === 'hidden') {
                return Promise.resolve();
            }

            clearTimeout(timer);
            timer = undefined;
            const version = generation;
            const requiredRevision = revision;
            const fresh = (result: Promise<void>) =>
                result.then(() => {
                    if (
                        active &&
                        document.visibilityState !== 'hidden' &&
                        generation === version &&
                        completedRevision < requiredRevision
                    ) {
                        return read();
                    }
                });
            if (pending) {
                return fresh(pending.result);
            }

            const controller = new AbortController();
            const current = () =>
                active &&
                document.visibilityState !== 'hidden' &&
                generation === version &&
                pending?.controller === controller &&
                !controller.signal.aborted;
            let polling = false;
            const result = Promise.resolve()
                .then(async () => {
                    if (!current()) {
                        return;
                    }
                    try {
                        const details = await api.activity.activityDetails({id}, {signal: controller.signal});
                        if (!current() || requiredRevision !== revision) {
                            return;
                        }
                        completedRevision = requiredRevision;
                        polling = isActive(details.entry);
                        setSnapshot({id, details, error: ''});
                    } catch (cause) {
                        if (current() && requiredRevision === revision) {
                            completedRevision = requiredRevision;
                            setSnapshot((previous) => ({
                                id,
                                details: previous.id === id ? previous.details : null,
                                error: errorMessage(cause),
                            }));
                        }
                    }
                })
                .finally(() => {
                    if (current()) {
                        pending = undefined;
                        if (polling) {
                            timer = setTimeout(() => void read(), POLL_INTERVAL_MS);
                        }
                    }
                });
            pending = {controller, result};
            return fresh(result);
        }

        function visibilityChanged() {
            if (document.visibilityState === 'hidden') {
                stop();
            } else {
                void read();
            }
        }

        const currentReader = {id, refresh: () => void read(true)};
        reader.current = currentReader;
        document.addEventListener('visibilitychange', visibilityChanged);
        void read();
        return () => {
            active = false;
            stop();
            if (reader.current === currentReader) {
                reader.current = null;
            }
            document.removeEventListener('visibilitychange', visibilityChanged);
        };
    }, [id]);

    function updateFeedback(feedback: Contract.ActivityFeedback | null) {
        if (reader.current?.id !== id) {
            return;
        }
        setSnapshot((previous) =>
            previous.id === id && previous.details
                ? {...previous, details: {...previous.details, feedback}}
                : previous,
        );
        reader.current.refresh();
    }

    return {
        details: snapshot.id === id ? snapshot.details : null,
        error: snapshot.id === id ? snapshot.error : '',
        updateFeedback,
    };
}
