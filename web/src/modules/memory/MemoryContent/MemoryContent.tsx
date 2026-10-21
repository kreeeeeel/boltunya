import {Box, Button, Flex, Icon, Label, Skeleton, Text, TextArea} from '@gravity-ui/uikit';
import {ArrowRotateRight, ArrowsRotateRight, FolderOpen, FileArrowUp} from '@gravity-ui/icons';
import {useState} from 'react';
import {useLocale, useTranslations} from 'next-intl';
import type {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import styles from '@/modules/memory/MemoryContent/MemoryContent.module.css';
import {MarkdownContent} from '@/shared/ui/MarkdownContent/MarkdownContent';
import {desktopBridge} from '@/shared/desktop/bridge';
import {errorMessage} from '@/shared/api/client';

export function MemoryContent({
    memory,
    chatId = '',
}: {
    readonly memory: ReturnType<typeof useMemoryDocument>;
    readonly chatId?: string;
}) {
    const t = useTranslations();
    const locale = useLocale();
    const document = memory.document;
    const [folderError, setFolderError] = useState('');
    const [folderBusy, setFolderBusy] = useState(false);

    async function openFolder() {
        setFolderError('');
        setFolderBusy(true);
        try {
            await desktopBridge()?.openStorageLocation('memory');
        } catch (cause) {
            setFolderError(errorMessage(cause));
        } finally {
            setFolderBusy(false);
        }
    }

    return (
        <>
            {!document && !memory.error ? (
                <Flex direction="column" gap={4} role="status" aria-label={t('loading')} qa="memory-skeleton">
                    <Skeleton height={40} />
                    <Flex justifyContent="space-between">
                        <Skeleton width={100} height={20} />
                        <Skeleton width={140} height={20} />
                    </Flex>
                    <Box>
                        <Skeleton width={180} height={20} />
                        <Skeleton height={100} />
                    </Box>
                    <Box>
                        <Skeleton width={160} height={20} />
                        <Skeleton height={120} />
                    </Box>
                </Flex>
            ) : (
                document && (
                    <Flex direction="column" gap={4}>
                        <Text color="secondary">{t(chatId ? 'memory.chatHint' : 'memory.globalHint')}</Text>
                        <Flex justifyContent="space-between" alignItems="center" gap={3} wrap>
                            <Label
                                theme={
                                    document.status === 'error'
                                        ? 'danger'
                                        : document.status === 'ready'
                                          ? 'success'
                                          : 'info'
                                }
                            >
                                {t('memory.status.' + document.status)}
                            </Label>
                            <Text color="secondary">
                                {t(chatId ? 'memory.scanned' : 'memory.tracked', {
                                    count: chatId ? document.processedMessages : document.trackedChats,
                                })}
                            </Text>
                        </Flex>
                        {document.updatedAt > 0 && (
                            <Text color="secondary">
                                {t('memory.updated', {
                                    date: new Date(document.updatedAt * 1000).toLocaleString(locale),
                                })}
                            </Text>
                        )}
                        <Box>
                            <Text as="label" htmlFor="memory-notes" className={styles.label}>
                                {t('memory.notes')}
                            </Text>
                            <TextArea
                                id="memory-notes"
                                value={memory.notes}
                                onUpdate={memory.setNotes}
                                minRows={4}
                                maxRows={9}
                                controlProps={{id: 'memory-notes', maxLength: 16000}}
                            />
                            <Text color="secondary" className={styles.hint}>
                                {t('memory.notesHint')}
                            </Text>
                        </Box>
                        <Box>
                            <Text className={styles.label}>
                                {t(chatId ? 'memory.summary' : 'memory.ownerSummary')}
                            </Text>
                            <Box className={styles.summary} qa="memory-summary">
                                <MarkdownContent>{document.summary || t('memory.empty')}</MarkdownContent>
                            </Box>
                            <Text color="secondary" className={styles.hint}>
                                {t(document.backfillComplete ? 'memory.complete' : 'memory.scanning')}
                            </Text>
                        </Box>
                        <Box className={styles.actions} qa="memory-actions">
                            <Button
                                view="flat-secondary"
                                size="m"
                                className={styles.action}
                                disabled={memory.busy || document.status === 'disabled'}
                                onClick={() => void memory.refresh()}
                            >
                                <Icon data={ArrowRotateRight} size={16} />
                                {t('memory.refresh')}
                            </Button>
                            <Button
                                view="flat-secondary"
                                size="m"
                                className={styles.action}
                                disabled={memory.busy || document.status === 'disabled'}
                                onClick={() => void memory.refresh(true)}
                            >
                                <Icon data={ArrowsRotateRight} size={16} />
                                {t('memory.rebuild')}
                            </Button>
                            <Button
                                view="flat-secondary"
                                size="m"
                                className={styles.action}
                                disabled={memory.busy}
                                onClick={() => void memory.reload()}
                            >
                                <Icon data={FileArrowUp} size={16} />
                                {t('memory.reload')}
                            </Button>
                            {desktopBridge() && (
                                <Button
                                    view="flat-secondary"
                                    size="m"
                                    className={styles.action}
                                    disabled={memory.busy || folderBusy}
                                    loading={folderBusy}
                                    onClick={() => void openFolder()}
                                >
                                    <Icon data={FolderOpen} size={16} />
                                    {t('storageLocations.open')}
                                </Button>
                            )}
                        </Box>
                        {document.error && <Text color="danger">{document.error}</Text>}
                        <Text color="secondary" className={styles.path}>
                            {document.relativePath}
                        </Text>
                        {folderError && (
                            <Text color="danger" role="alert">
                                {folderError}
                            </Text>
                        )}
                    </Flex>
                )
            )}
            {memory.error && (
                <Flex direction="column" gap={3}>
                    <Text color="danger">{memory.error}</Text>
                    {!document && <Button onClick={() => void memory.reload()}>{t('retry')}</Button>}
                </Flex>
            )}
        </>
    );
}
