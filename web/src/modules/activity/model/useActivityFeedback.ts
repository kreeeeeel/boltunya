import {useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
type Feedback = Contract.ActivityFeedback;
type FeedbackRating = Contract.ActivityFeedbackRating;
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
                feedback = await api.activity.saveActivityFeedback({
                    id,
                    activityFeedbackUpdate: {rating, ...(comment === undefined ? {} : {comment})},
                });
            } else {
                await api.activity.removeActivityFeedback({
                    id,
                });
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
