import {useEffect, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';

export function useSpeechStorage(installedAssets: string) {
    const desktop = desktopBridge();
    const [openable, setOpenable] = useState(false);
    const [revision, setRevision] = useState(0);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        const controller = new AbortController();
        if (desktop) {
            void api.system
                .storageLocations({signal: controller.signal})
                .then(({locations}) => {
                    if (!controller.signal.aborted) {
                        setOpenable(
                            locations.some((location) => location.id === 'speech' && location.openable),
                        );
                    }
                })
                .catch((cause) => {
                    if (!controller.signal.aborted) {
                        setError(errorMessage(cause));
                    }
                });
        }
        return () => controller.abort();
    }, [desktop, revision, installedAssets]);

    async function browse() {
        setBusy(true);
        setError('');
        try {
            await desktop?.openStorageLocation('speech');
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }

    return {
        desktop: Boolean(desktop),
        browse: desktop && openable ? browse : undefined,
        reload: () => setRevision((current) => current + 1),
        error,
        busy,
    };
}
