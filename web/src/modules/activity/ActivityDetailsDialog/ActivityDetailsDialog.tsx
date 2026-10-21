import {Box, Button, Dialog, Flex, Label, Skeleton, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';

import {ActivityAttempt} from '@/modules/activity/ActivityAttempt/ActivityAttempt';
import {useActivityDetails} from '@/modules/activity/model/useActivityDetails';
import {isActive, seconds, statusLabelKey, statusTheme} from '@/modules/activity/model/activity';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import styles from '@/modules/activity/ActivityDetailsDialog/ActivityDetailsDialog.module.css';
import {ActivityFeedbackEditor} from '@/modules/activity/ActivityFeedbackEditor/ActivityFeedbackEditor';

export function ActivityDetailsDialog({
    id,
    onClose,
    onRefresh,
}: {
    readonly id: string;
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations('events');
    const locale = useLocale();
    const {details, error, updateFeedback} = useActivityDetails(id);
    const entry = details?.entry;
    return (
        <Dialog
            open
            maxWidth="m"
            fullWidth
            contentOverflow="auto"
            onClose={onClose}
            aria-label={t(entry?.kind === 'memory' ? 'memoryDetails' : 'details')}
            qa="event-details"
        >
            <Dialog.Header caption={t(entry?.kind === 'memory' ? 'memoryDetails' : 'details')} />
            <Dialog.Body>
                {!details && !error && (
                    <Flex direction="column" gap={5} role="status" aria-busy="true" aria-label={t('details')}>
                        <Flex gap={3} alignItems="center">
                            <Skeleton variant="circle" width={40} height={40} />
                            <Skeleton width={180} height={36} />
                        </Flex>
                        <Box className={styles.stats}>
                            {Array.from({length: 4}, (_, index) => (
                                <Skeleton key={index} height={44} />
                            ))}
                        </Box>
                        <Skeleton height={24} width="70%" />
                        {[
                            styles.message,
                            styles.requestBlock,
                            styles.thinkingBlock,
                            styles.responseBlock,
                        ].map((style, index) => (
                            <Flex
                                key={index}
                                direction="column"
                                gap={3}
                                className={[styles.traceBlock, style].join(' ')}
                            >
                                <Skeleton width="45%" height={20} />
                                <Skeleton height={70} />
                            </Flex>
                        ))}
                    </Flex>
                )}
                {error && (
                    <Text color="danger" role="alert">
                        {error}
                    </Text>
                )}
                {details && entry && (
                    <Flex direction="column" gap={5}>
                        <Flex alignItems="center" gap={3} justifyContent="space-between">
                            <Flex alignItems="center" gap={3}>
                                <TelegramAvatar title={entry.title} url={entry.avatarUrl} />
                                <Flex direction="column" gap={1}>
                                    <Text variant="subheader-3">{entry.title}</Text>
                                    <Text color="secondary">
                                        {new Date(entry.date * 1000).toLocaleString(locale)}
                                    </Text>
                                </Flex>
                            </Flex>
                            <Label size="m" theme={statusTheme(entry.status)}>
                                {t(statusLabelKey(entry))}
                            </Label>
                        </Flex>
                        <Box className={styles.stats}>
                            {[
                                [
                                    t(entry.kind === 'memory' ? 'taskTime' : 'responseTime'),
                                    t('seconds', {value: seconds(entry.durationMs, locale)}),
                                ],
                                [
                                    t('inputTokens'),
                                    (entry.kind === 'memory' && entry.inputTokens != null) ||
                                    details.attempts.some((attempt) => attempt.complete)
                                        ? (entry.inputTokens ?? 0)
                                        : '—',
                                ],
                                [
                                    t('outputTokens'),
                                    (entry.kind === 'memory' && entry.outputTokens != null) ||
                                    details.attempts.some((attempt) => attempt.complete)
                                        ? (entry.outputTokens ?? 0)
                                        : '—',
                                ],
                                [t('requests'), details.attempts.length],
                            ].map(([label, value]) => (
                                <Flex direction="column" gap={2} key={label}>
                                    <Text color="secondary">{label}</Text>
                                    <Text variant="subheader-3">{value}</Text>
                                </Flex>
                            ))}
                        </Box>
                        {details.attempts.some((attempt) => !attempt.complete) && (
                            <Text color="secondary">{t('pendingMetrics')}</Text>
                        )}
                        <Flex gap={2} wrap className={styles.timeline}>
                            {details.steps.map((step, index) => (
                                <Label key={index} title={new Date(step.date).toLocaleTimeString(locale)}>
                                    {t(`phase.${step.phase}`)}
                                </Label>
                            ))}
                        </Flex>
                        {entry.kind !== 'memory' && (
                            <Box className={styles.message}>
                                <Text variant="subheader-1">{t('trigger')}</Text>
                                <Box as="pre" className={styles.pre}>
                                    {details.trigger}
                                </Box>
                            </Box>
                        )}
                        {entry.status === 'error' && <Text color="danger">{entry.detail}</Text>}
                        {details.response && (
                            <Box className={styles.delivered}>
                                <Text variant="subheader-1">
                                    {t(entry.status === 'sent' ? 'delivered' : 'readyResponse')}
                                </Text>
                                <Box as="pre" className={styles.pre}>
                                    {details.response}
                                </Box>
                            </Box>
                        )}
                        {entry.feedbackAvailable !== false && !isActive(entry) && details.response.trim() && (
                            <ActivityFeedbackEditor
                                id={id}
                                value={details.feedback ?? null}
                                onUpdated={updateFeedback}
                                onRefresh={onRefresh}
                            />
                        )}
                        {details.attempts.map((attempt) => (
                            <ActivityAttempt key={attempt.number} attempt={attempt} />
                        ))}
                        {!details.attempts.length && (
                            <Text color="secondary">
                                {t(entry.kind === 'memory' ? 'memoryPrivacy' : 'noRequests')}
                            </Text>
                        )}
                    </Flex>
                )}
            </Dialog.Body>
            <Box className={styles.footer}>
                <Button onClick={onClose} view="normal" size="l">
                    {t('close')}
                </Button>
            </Box>
        </Dialog>
    );
}
