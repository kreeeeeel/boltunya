import {useCallback, useEffect, useRef, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';

const UPDATE_EVENT = 'speech-assets-updated';
const POLL_INTERVAL = 1000;

export function useSpeechState(enabled = true) {
    const [state, setState] = useState<Awaited<ReturnType<typeof api.speech.speechState>>>();
    const [error, setError] = useState('');
    const [busy, setBusy] = useState('');
    const mounted = useRef(false);
    const [revision, setRevision] = useState(0);
    const reload = useCallback(() => setRevision((previous) => previous + 1), []);

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
        };
    }, []);

    useEffect(() => {
        if (!enabled) {
            return;
        }
        const controller = new AbortController();
        let timer: ReturnType<typeof setTimeout>;
        async function read() {
            try {
                const next = await api.speech.speechState({signal: controller.signal});
                if (!controller.signal.aborted) {
                    setState(next);
                    setError('');
                    if (
                        next.downloads.some((download) =>
                            ['queued', 'downloading', 'extracting'].includes(download.phase),
                        )
                    ) {
                        timer = setTimeout(() => void read(), POLL_INTERVAL);
                    }
                }
            } catch (cause) {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            }
        }
        void read();
        const updated = () => reload();
        window.addEventListener(UPDATE_EVENT, updated);
        return () => {
            controller.abort();
            clearTimeout(timer);
            window.removeEventListener(UPDATE_EVENT, updated);
        };
    }, [enabled, reload, revision]);

    async function change(assetId: string, cancel: boolean) {
        setBusy(assetId);
        setError('');
        try {
            if (cancel) {
                await api.speech.cancelSpeechAsset({assetId});
            } else {
                await api.speech.installSpeechAsset({assetId});
            }
            if (mounted.current) {
                window.dispatchEvent(new Event(UPDATE_EVENT));
            }
        } catch (cause) {
            if (mounted.current) {
                setError(errorMessage(cause));
            }
        } finally {
            if (mounted.current) {
                setBusy('');
            }
        }
    }

    return {
        state,
        error,
        busy,
        reload,
        install: (id: string) => change(id, false),
        cancel: (id: string) => change(id, true),
    };
}
