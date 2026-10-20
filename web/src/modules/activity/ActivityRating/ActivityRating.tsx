import {Comment, ThumbsDown, ThumbsUp} from '@gravity-ui/icons';
import {Button, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';

import type {components} from '@/shared/api/schema';
import {useActivityFeedback} from '@/modules/activity/model/useActivityFeedback';
import styles from '@/modules/activity/ActivityRating/ActivityRating.module.css';

export function ActivityRating({
    id,
    rating,
    onDetails,
    onRefresh,
}: {
    readonly id: string;
    readonly rating?: components['schemas']['ActivityFeedbackRating'] | null;
    readonly onDetails: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations('events.feedback');
    const [selection, setSelection] = useState({incoming: rating, value: rating ?? null});
    if (selection.incoming !== rating) {
        setSelection({incoming: rating, value: rating ?? null});
    }
    const value = selection.value;
    const feedback = useActivityFeedback(
        id,
        (result) => {
            setSelection({incoming: rating, value: result?.rating ?? null});
        },
        onRefresh,
    );
    return (
        <Flex direction="column" gap={2} className={styles.root} qa="activity-rating">
            <Flex alignItems="center" gap={2}>
                <Text color="secondary" className={styles.label}>
                    {t('title')}
                </Text>
                {(['good', 'bad'] as const).map((option) => (
                    <Tooltip key={option} content={t(value === option ? 'remove' : option)}>
                        <Button
                            view={
                                value === option
                                    ? option === 'good'
                                        ? 'flat-success'
                                        : 'flat-danger'
                                    : 'flat-secondary'
                            }
                            selected={value === option}
                            aria-label={t(option)}
                            aria-pressed={value === option}
                            disabled={feedback.busy}
                            onClick={() => void feedback.update(value === option ? null : option)}
                        >
                            <Icon data={option === 'good' ? ThumbsUp : ThumbsDown} size={18} />
                        </Button>
                    </Tooltip>
                ))}
                <Tooltip content={t('commentAction')}>
                    <Button
                        view="flat-secondary"
                        aria-label={t('commentAction')}
                        disabled={feedback.busy}
                        onClick={onDetails}
                    >
                        <Icon data={Comment} size={18} />
                    </Button>
                </Tooltip>
            </Flex>
            {feedback.error && (
                <Text color="danger" role="alert">
                    {feedback.error}
                </Text>
            )}
            {feedback.refreshError && (
                <Text color="warning" role="alert">
                    {t('refreshFailed')}
                </Text>
            )}
        </Flex>
    );
}
