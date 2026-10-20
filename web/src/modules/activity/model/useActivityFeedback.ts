import {useState} from 'react';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

type Feedback = components['schemas']['ActivityFeedback'];
type FeedbackRating = components['schemas']['ActivityFeedbackRating'];

export function useActivityFeedback(
    id: string,
    onUpdated: (feedback: Feedback | null) => void,
    onRefresh?: () => Promise<void>,
) {
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [refreshError, setRefreshError] = useState('');

    async function update(rating: FeedbackRating | null, comment?: string) {
        setBusy(true);
        setError('');
        setRefreshError('');
        let feedback: Feedback | null = null;
        try {
            if (rating) {
                feedback = dataOrThrow(
                    await api.PUT('/api/activity/{id}/feedback', {
                        params: {path: {id}},
                        body: {rating, ...(comment === undefined ? {} : {comment})},
                    }),
                );
            } else {
                dataOrThrow(await api.DELETE('/api/activity/{id}/feedback', {params: {path: {id}}}));
            }
        } catch (cause) {
            setError(errorMessage(cause));
            return false;
        } finally {
            setBusy(false);
        }
        onUpdated(feedback);
        if (onRefresh) {
            void onRefresh().catch((cause) => setRefreshError(errorMessage(cause)));
        }
        return true;
    }

    return {busy, error, refreshError, update};
}
