import {useEffect, useState} from 'react';

import {isActive} from '@/modules/activity/model/activity';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {ActivityDetails} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

export function useActivityDetails(id: string) {
    const [details, setDetails] = useState<ActivityDetails | null>(null);
    const [error, setError] = useState('');
    useEffect(() => {
        const controller = new AbortController();
        let timer: ReturnType<typeof setTimeout>;
        async function poll() {
            try {
                const result = dataOrThrow(
                    await api.GET('/api/activity/{id}', {params: {path: {id}}, signal: controller.signal}),
                );
                if (controller.signal.aborted) {
                    return;
                }
                setDetails(result);
                setError('');
                if (isActive(result.entry)) {
                    timer = setTimeout(poll, 1000);
                }
            } catch (cause) {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            }
        }
        void poll();
        return () => {
            controller.abort();
            clearTimeout(timer);
        };
    }, [id]);
    function updateFeedback(feedback: components['schemas']['ActivityFeedback'] | null) {
        setDetails((previous) => (previous ? {...previous, feedback} : previous));
    }
    return {details, error, updateFeedback};
}
