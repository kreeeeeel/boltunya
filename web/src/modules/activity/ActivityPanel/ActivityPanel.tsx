import {CirclePause, CirclePlay, Clock, Sliders, TrashBin} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Label, Loader, Text, Tooltip} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import {ActivityCard} from '@/modules/activity/ActivityCard/ActivityCard';
import {ActivityDetailsDialog} from '@/modules/activity/ActivityDetailsDialog/ActivityDetailsDialog';
import {isActive, mergeActivityEntries, orderActivityEntries} from '@/modules/activity/model/activity';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Activity, ReplyQueue} from '@/shared/api/client';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import styles from '@/modules/activity/ActivityPanel/ActivityPanel.module.css';
import {ParallelSettingsDialog} from '@/modules/activity/ParallelSettingsDialog/ParallelSettingsDialog';
import {HistorySettingsDialog} from '@/modules/activity/HistorySettingsDialog/HistorySettingsDialog';
import {useActivityHistory} from '@/modules/activity/model/useActivityHistory';

export function ActivityPanel({
    entries: currentEntries,
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
    const [historyOpen, setHistoryOpen] = useState(false);
    const history = useActivityHistory();
    const archived = new Map(history.entries.map((entry) => [entry.id, entry]));
    const entries = mergeActivityEntries(
        history.entries,
        currentEntries.map((entry) => (!isActive(entry) ? (archived.get(entry.id) ?? entry) : entry)),
    );
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
    const ordered = orderActivityEntries(entries);
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
                {entries.length === 0 && history.loading ? (
                    <Flex alignItems="center" justifyContent="center" className={styles.empty}>
                        <Loader size="m" />
                    </Flex>
                ) : entries.length === 0 ? (
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
                                    aria-label={t('events.archive.title')}
                                    onClick={() => setHistoryOpen(true)}
                                >
                                    <Icon data={Clock} size={18} />
                                </Button>
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
                                            void act('clear', async () => {
                                                dataOrThrow(await api.DELETE('/api/activity'));
                                                await history.refresh(true);
                                            })
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
                        {history.storage && !history.storage.recording && (
                            <Text role="alert" color="warning" className={styles.privacy}>
                                {t('events.archive.notRecording')}{' '}
                                <Button view="flat-secondary" onClick={() => setHistoryOpen(true)}>
                                    {t('events.archive.title')}
                                </Button>
                            </Text>
                        )}
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
                        {history.hasMore && (
                            <Flex justifyContent="center" className={styles.more}>
                                <Button
                                    size="l"
                                    view="normal"
                                    loading={history.loadingMore}
                                    disabled={history.loadingMore}
                                    onClick={() => void history.loadMore()}
                                >
                                    {t('events.archive.loadMore')}
                                </Button>
                            </Flex>
                        )}
                    </>
                )}
                {error && (
                    <Text color="danger" role="alert">
                        {error}
                    </Text>
                )}
                {history.error && (
                    <Text color="danger" role="alert">
                        {history.error}
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
            {historyOpen && (
                <HistorySettingsDialog
                    onClose={() => setHistoryOpen(false)}
                    onRefresh={() => history.refresh(true)}
                />
            )}
        </ScrollArea>
    );
}
