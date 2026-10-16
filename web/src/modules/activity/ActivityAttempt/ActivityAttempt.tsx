import {ArrowUpFromSquare, Comments, Sparkles} from '@gravity-ui/icons';
import {Box, Disclosure, Flex, Icon, Label, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';

import {seconds} from '@/modules/activity/model/activity';
import type {ActivityAttempt as Attempt} from '@/shared/api/client';
import styles from '@/modules/activity/ActivityDetailsDialog/ActivityDetailsDialog.module.css';

export function ActivityAttempt({attempt}: {readonly attempt: Attempt}) {
    const t = useTranslations('events');
    const locale = useLocale();
    return (
        <Flex direction="column" gap={4} className={styles.attempt}>
            <Flex alignItems="center" gap={2} wrap>
                <Text variant="subheader-2">{t('attempt', {count: attempt.number})}</Text>
                <Label theme="info">{t(`phase.${attempt.purpose}`)}</Label>
                <Text color="secondary">{attempt.model}</Text>
            </Flex>
            <Flex gap={3} wrap className={styles.meta}>
                <Text>{t('seconds', {value: seconds(attempt.durationMs, locale)})}</Text>
                {attempt.complete ? (
                    <>
                        <Text>
                            {t('tokenPair', {input: attempt.inputTokens, output: attempt.outputTokens})}
                        </Text>
                        <Text>{t('speed', {value: attempt.tokensPerSecond.toFixed(1)})}</Text>
                        <Label>{attempt.finishReason || t('complete')}</Label>
                    </>
                ) : (
                    <Label loading>{t('partial')}</Label>
                )}
            </Flex>
            {attempt.complete && (
                <Text color="secondary">
                    {t('timings', {
                        load: seconds(attempt.loadDurationMs, locale),
                        prompt: seconds(attempt.promptDurationMs, locale),
                    })}
                </Text>
            )}
            <Box
                className={`${styles.traceBlock} ${styles.requestBlock}`}
                qa={`event-request-${attempt.number}`}
            >
                <Flex alignItems="center" gap={3} className={styles.blockHeading}>
                    <Icon data={ArrowUpFromSquare} size={20} />
                    <Text variant="subheader-2">{t('requestTitle')}</Text>
                </Flex>
                <Disclosure size="l" summary={t('request', {count: attempt.messages.length})}>
                    <Flex direction="column" gap={3} className={styles.traceContent}>
                        {attempt.messages.map((message, index) => (
                            <Box key={index} className={styles.message}>
                                <Label theme={message.role === 'system' ? 'utility' : 'normal'}>
                                    {message.role}
                                </Label>
                                <Box as="pre" className={styles.pre}>
                                    {message.text}
                                </Box>
                            </Box>
                        ))}
                    </Flex>
                </Disclosure>
            </Box>
            <Box
                className={`${styles.traceBlock} ${styles.thinkingBlock}`}
                qa={`event-thinking-${attempt.number}`}
            >
                <Flex alignItems="center" gap={3} className={styles.blockHeading}>
                    <Icon data={Sparkles} size={20} />
                    <Text variant="subheader-2">{t('thinking')}</Text>
                </Flex>
                <Box as="pre" className={styles.pre}>
                    {attempt.thinking || t(attempt.complete ? 'noThinking' : 'waitingThinking')}
                </Box>
            </Box>
            <Box
                className={`${styles.traceBlock} ${styles.responseBlock}`}
                qa={`event-response-${attempt.number}`}
            >
                <Flex alignItems="center" gap={3} className={styles.blockHeading}>
                    <Icon data={Comments} size={20} />
                    <Text variant="subheader-2">{t('rawResponse')}</Text>
                </Flex>
                <Box as="pre" className={styles.pre}>
                    {attempt.response || t(attempt.complete ? 'emptyResponse' : 'waitingResponse')}
                </Box>
            </Box>
            <Box className={styles.traceBlock} qa={`event-options-${attempt.number}`}>
                <Disclosure size="l" summary={t('options')}>
                    <Box as="pre" className={styles.pre}>
                        {JSON.stringify(JSON.parse(attempt.options), null, 2)}
                    </Box>
                </Disclosure>
            </Box>
        </Flex>
    );
}
