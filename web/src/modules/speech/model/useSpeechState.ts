import {useCallback, useEffect, useRef, useState, useSyncExternalStore} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import {speechStateStore} from '@/modules/speech/model/speechStateStore';

const inactiveSubscription = () => () => {};

export function useSpeechState(enabled = true) {
    const snapshot = useSyncExternalStore(
        enabled ? speechStateStore.subscribe : inactiveSubscription,
        speechStateStore.getSnapshot,
        speechStateStore.getServerSnapshot,
    );
    const [actionError, setActionError] = useState('');
    const [busy, setBusy] = useState('');
    const mounted = useRef(false);
    const reload = useCallback(() => {
        if (mounted.current) {
            setActionError('');
        }
        speechStateStore.refresh();
    }, []);

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
        };
    }, []);

    async function change(assetId: string, cancel: boolean) {
        setBusy(assetId);
        setActionError('');
        try {
            if (cancel) {
                await api.speech.cancelSpeechAsset({assetId});
            } else {
                await api.speech.installSpeechAsset({assetId});
            }
            speechStateStore.refresh();
        } catch (cause) {
            if (mounted.current) {
                setActionError(errorMessage(cause));
            }
        } finally {
            if (mounted.current) {
                setBusy('');
            }
        }
    }

    return {
        state: snapshot.state,
        error: actionError || snapshot.error,
        busy,
        reload,
        install: (id: string) => change(id, false),
        cancel: (id: string) => change(id, true),
    };
}
