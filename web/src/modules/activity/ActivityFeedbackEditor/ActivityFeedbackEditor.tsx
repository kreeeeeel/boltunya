import {Check, ThumbsDown, ThumbsUp, TrashBin} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Text, TextArea} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';

import type {components} from '@/shared/api/schema';
import {useActivityFeedback} from '@/modules/activity/model/useActivityFeedback';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import styles from '@/modules/activity/ActivityFeedbackEditor/ActivityFeedbackEditor.module.css';

type Feedback = components['schemas']['ActivityFeedback'];

export function ActivityFeedbackEditor({
    id,
    value,
    onUpdated,
    onRefresh,
}: {
    readonly id: string;
    readonly value: Feedback | null;
    readonly onUpdated: (value: Feedback | null) => void;
    readonly onRefresh?: () => Promise<void>;
}) {
    const t = useTranslations('events.feedback');
    const [rating, setRating] = useState(value?.rating ?? null);
    const [comment, setComment] = useState(value?.comment ?? '');
    const saved = useSaveFeedback(`${rating}:${comment}`);
    const feedback = useActivityFeedback(
        id,
        (result) => {
            if (!result) {
                setRating(null);
                setComment('');
            }
            onUpdated(result);
        },
        onRefresh,
    );
    const unchanged = rating === (value?.rating ?? null) && comment === (value?.comment ?? '');

    async function save() {
        if (!rating) {
            return;
        }
        saved.clearSaved();
        if (await feedback.update(rating, comment)) {
            saved.markSaved();
        }
    }

    return (
        <Box className={styles.root} qa="activity-feedback-editor">
            <Flex direction="column" gap={3}>
                <Text variant="subheader-2">{t('title')}</Text>
                <Text color="secondary">{t('hint')}</Text>
                <Flex gap={2}>
                    {(['good', 'bad'] as const).map((option) => (
                        <Button
                            key={option}
                            size="l"
                            view={rating === option ? 'outlined-info' : 'normal'}
                            selected={rating === option}
                            aria-pressed={rating === option}
                            disabled={feedback.busy}
                            onClick={() => setRating(option)}
                        >
                            <Icon data={option === 'good' ? ThumbsUp : ThumbsDown} size={18} />
                            {t(option)}
                        </Button>
                    ))}
                </Flex>
                <Text>{t('comment')}</Text>
                <TextArea
                    value={comment}
                    onUpdate={setComment}
                    minRows={3}
                    maxRows={6}
                    disabled={feedback.busy}
                    controlProps={{'aria-label': t('comment'), maxLength: 1000}}
                />
                <Flex gap={2} alignItems="center" wrap>
                    <Button
                        view="action"
                        size="l"
                        loading={feedback.busy}
                        disabled={!rating || unchanged}
                        onClick={() => void save()}
                        aria-label={t(saved.saved ? 'saved' : 'save')}
                    >
                        {saved.saved && <Icon data={Check} size={18} />}
                        <Text role={saved.saved ? 'status' : undefined}>
                            {t(saved.saved ? 'saved' : 'save')}
                        </Text>
                    </Button>
                    {value && (
                        <Button
                            view="flat-danger"
                            size="l"
                            disabled={feedback.busy}
                            onClick={() => {
                                saved.clearSaved();
                                void feedback.update(null);
                            }}
                        >
                            <Icon data={TrashBin} size={18} />
                            {t('remove')}
                        </Button>
                    )}
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
                <Text color="secondary" className={styles.privacy}>
                    {t('privacy')}
                </Text>
            </Flex>
        </Box>
    );
}
