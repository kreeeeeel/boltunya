import {ArrowUpToLine, CircleStop, Clock, Sparkles} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Loader, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';

import {isActive, seconds, statusTheme} from '@/modules/activity/model/activity';
import type {Activity} from '@/shared/api/client';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import styles from '@/modules/activity/ActivityCard/ActivityCard.module.css';

export function ActivityCard({
    entry,
    busy,
    onOpen,
    onCancel,
    onPriority,
}: {
    readonly entry: Activity;
    readonly busy: boolean;
    readonly onOpen: () => void;
    readonly onCancel: () => void;
    readonly onPriority: () => void;
}) {
    const t = useTranslations('events');
    const locale = useLocale();
    const active = isActive(entry);
    const running = entry.status === 'generating';
    return (
        <Box
            as="article"
            className={`${styles.card} ${running ? styles.running : ''}`}
            qa={`event-${entry.id}`}
        >
            <Button
                className={styles.open}
                view="flat"
                onClick={onOpen}
                title={t('history')}
                aria-label={t('open', {name: entry.title})}
            />
            <Flex alignItems="center" justifyContent="space-between" gap={3} className={styles.heading}>
                <Flex alignItems="center" gap={3} className={styles.identity}>
                    <TelegramAvatar title={entry.title} url={entry.avatarUrl} />
                    <Flex direction="column" gap={1} className={styles.name}>
                        <Text variant="subheader-2" ellipsis>
                            {entry.title}
                        </Text>
                        <Text color="secondary" ellipsis>
                            {entry.status === 'error' ? t('failed') : entry.detail}
                        </Text>
                    </Flex>
                </Flex>
                <Flex gap={2} alignItems="center" wrap>
                    {entry.priority && (
                        <Label icon={<Icon data={ArrowUpToLine} size={13} />}>{t('priority')}</Label>
                    )}
                    <Label theme={statusTheme(entry.status)} size="m" loading={running}>
                        {t(`status.${entry.status}`)}
                    </Label>
                </Flex>
            </Flex>
            {active ? (
                <Box className={styles.process}>
                    <Flex alignItems="center" gap={3}>
                        {running ? <Loader size="s" /> : <Icon data={Clock} size={18} />}
                        <Text variant="subheader-1">
                            {entry.status === 'queued'
                                ? t('position', {count: entry.queuePosition ?? 0})
                                : t(`phase.${entry.phase ?? 'context'}`)}
                        </Text>
                    </Flex>
                    {entry.thinking ? (
                        <Text className={styles.thinking}>{entry.thinking}</Text>
                    ) : (
                        <Text color="secondary" className={styles.preview}>
                            {entry.preview}
                        </Text>
                    )}
                    {running && (
                        <Box className={styles.progress} role="progressbar" aria-label={t('processing')} />
                    )}
                </Box>
            ) : (
                <Text className={styles.preview} color={entry.status === 'error' ? 'danger' : 'secondary'}>
                    {entry.status === 'error' ? entry.detail : entry.preview}
                </Text>
            )}
            <Flex alignItems="center" justifyContent="space-between" gap={3} wrap className={styles.footer}>
                <Flex alignItems="center" gap={3} className={styles.metrics}>
                    <Icon data={Sparkles} size={16} />
                    <Text color="secondary">
                        {entry.durationMs
                            ? t('seconds', {value: seconds(entry.durationMs, locale)})
                            : t('waiting')}
                    </Text>
                    {!!entry.outputTokens && (
                        <Text color="secondary">
                            {t('tokens', {count: (entry.inputTokens ?? 0) + entry.outputTokens})}
                        </Text>
                    )}
                </Flex>
                {active && (
                    <Flex gap={2} className={styles.actions}>
                        {entry.status === 'queued' && (
                            <Button
                                view="flat-secondary"
                                disabled={busy || entry.priority}
                                onClick={onPriority}
                                aria-label={t('prioritize', {name: entry.title})}
                            >
                                <Icon data={ArrowUpToLine} size={16} />
                                {t('priority')}
                            </Button>
                        )}
                        <Button
                            view="outlined-danger"
                            disabled={busy || entry.phase === 'sending'}
                            onClick={onCancel}
                            aria-label={t('stopNamed', {name: entry.title})}
                        >
                            <Icon data={CircleStop} size={16} />
                            {t('stop')}
                        </Button>
                    </Flex>
                )}
            </Flex>
        </Box>
    );
}
