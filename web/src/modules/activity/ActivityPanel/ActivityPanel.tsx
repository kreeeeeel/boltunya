import {CirclePause, CirclePlay, Sliders, TrashBin} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Text, Tooltip} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import {ActivityCard} from '@/modules/activity/ActivityCard/ActivityCard';
import {ActivityDetailsDialog} from '@/modules/activity/ActivityDetailsDialog/ActivityDetailsDialog';
import {isActive} from '@/modules/activity/model/activity';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Activity, ReplyQueue} from '@/shared/api/client';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import styles from '@/modules/activity/ActivityPanel/ActivityPanel.module.css';
import {ParallelSettingsDialog} from '@/modules/activity/ParallelSettingsDialog/ParallelSettingsDialog';

export function ActivityPanel({
    entries,
    queue,
    assistantPaused,
    maxParallelTasks = 1,
    onRefresh,
}: {
    readonly entries: Activity[];
    readonly queue?: ReplyQueue;
    readonly assistantPaused: boolean;
    readonly maxParallelTasks?: number;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations();
    const [selected, setSelected] = useState<string | null>(null);
    const [parallelOpen, setParallelOpen] = useState(false);
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    async function act(key: string, action: () => Promise<unknown>) {
        setBusy(key);
        setError('');
        try {
            await action();
            await onRefresh();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    const queued = queue?.queued ?? entries.filter((entry) => entry.status === 'queued').length;
    const ordered = [...entries].sort((a, b) => {
        const rank = (entry: Activity) =>
            entry.status === 'generating' ? 0 : entry.status === 'queued' ? 1 : 2;
        return (
            rank(a) - rank(b) ||
            (a.status === 'queued' ? (a.queuePosition ?? 0) - (b.queuePosition ?? 0) : b.date - a.date)
        );
    });
    const enableButton = assistantPaused ? (
        <Button
            view="action"
            size="l"
            loading={busy === 'enable'}
            disabled={!!busy}
            onClick={() =>
                void act('enable', async () =>
                    dataOrThrow(await api.PATCH('/api/settings', {body: {paused: false}})),
                )
            }
        >
            <Icon data={CirclePlay} size={18} />
            {t('events.enableAi')}
        </Button>
    ) : null;
    return (
        <ScrollArea className={styles.root}>
            <Box className={styles.content}>
                {entries.length === 0 ? (
                    <Flex direction="column" gap={3} className={styles.empty} qa="empty-activity">
                        <Box
                            as="img"
                            src="/assets/illustrations/activity.png"
                            alt=""
                            className={styles.illustration}
                        />
                        <Text variant="header-1">{t('noActivity')}</Text>
                        <Text color="secondary">{t('noActivityText')}</Text>
                        {enableButton}
                    </Flex>
                ) : (
                    <>
                        <Flex
                            gap={3}
                            justifyContent="space-between"
                            alignItems="center"
                            wrap
                            className={styles.toolbar}
                            qa="events-toolbar"
                        >
                            <Flex alignItems="center" gap={3} wrap>
                                <Text variant="subheader-3">{t('events.queued', {count: queued})}</Text>
                                {(queue?.paused || assistantPaused) && (
                                    <Label theme="warning">
                                        {t(queue?.paused ? 'events.queuePaused' : 'events.assistantPaused')}
                                    </Label>
                                )}
                            </Flex>
                            <Flex gap={2}>
                                <Button
                                    view="flat-secondary"
                                    aria-label={t('events.parallel.title')}
                                    onClick={() => setParallelOpen(true)}
                                >
                                    <Icon data={Sliders} size={18} />
                                </Button>
                                <Button
                                    view="flat-secondary"
                                    disabled={!!busy}
                                    onClick={() =>
                                        void act('pause', async () =>
                                            dataOrThrow(
                                                await api.PATCH('/api/activity/queue', {
                                                    body: {paused: !queue?.paused},
                                                }),
                                            ),
                                        )
                                    }
                                >
                                    <Icon data={queue?.paused ? CirclePlay : CirclePause} size={18} />
                                    {t(queue?.paused ? 'events.resume' : 'events.pause')}
                                </Button>
                                <Tooltip content={t('events.clearHint')}>
                                    <Button
                                        view="flat-secondary"
                                        aria-label={t('events.clear')}
                                        disabled={!!busy || !entries.some((entry) => !isActive(entry))}
                                        onClick={() =>
                                            void act('clear', async () =>
                                                dataOrThrow(await api.DELETE('/api/activity')),
                                            )
                                        }
                                    >
                                        <Icon data={TrashBin} size={18} />
                                    </Button>
                                </Tooltip>
                            </Flex>
                        </Flex>
                        <Text className={styles.privacy} color="secondary">
                            {t('events.retention')}
                        </Text>
                        {assistantPaused && <Box className={styles.enable}>{enableButton}</Box>}
                        <Box className={styles.cards} qa="events-grid">
                            {ordered.map((entry) => (
                                <ActivityCard
                                    key={entry.id}
                                    entry={entry}
                                    busy={!!busy}
                                    onOpen={() => setSelected(entry.id)}
                                    onRefresh={onRefresh}
                                    onCancel={() =>
                                        void act(entry.id, async () =>
                                            dataOrThrow(
                                                await api.POST('/api/activity/{id}/cancel', {
                                                    params: {path: {id: entry.id}},
                                                }),
                                            ),
                                        )
                                    }
                                    onPriority={() =>
                                        void act(entry.id, async () =>
                                            dataOrThrow(
                                                await api.POST('/api/activity/{id}/priority', {
                                                    params: {path: {id: entry.id}},
                                                }),
                                            ),
                                        )
                                    }
                                />
                            ))}
                        </Box>
                    </>
                )}
                {error && (
                    <Text color="danger" role="alert">
                        {error}
                    </Text>
                )}
            </Box>
            {selected && (
                <ActivityDetailsDialog
                    key={selected}
                    id={selected}
                    onClose={() => setSelected(null)}
                    onRefresh={onRefresh}
                />
            )}
            {parallelOpen && (
                <ParallelSettingsDialog
                    value={queue?.maxParallelTasks ?? maxParallelTasks}
                    onClose={() => setParallelOpen(false)}
                    onRefresh={onRefresh}
                />
            )}
        </ScrollArea>
    );
}
