'use client';

import {Box, Button, Dialog, Flex, Label, Skeleton, Text, TextArea} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import styles from '@/modules/memory/MemoryDialog/MemoryDialog.module.css';

export function MemoryDialog({
    chatId = '',
    title = '',
    onClose,
}: {
    readonly chatId?: string;
    readonly title?: string;
    readonly onClose: () => void;
}) {
    const t = useTranslations();
    const locale = useLocale();
    const memory = useMemoryDocument(chatId);
    const document = memory.document;
    return (
        <Dialog
            open
            contentOverflow="auto"
            maxWidth="s"
            fullWidth
            onClose={onClose}
            aria-label={t(chatId ? 'memory.chatTitle' : 'memory.title', {name: title})}
        >
            <Dialog.Header caption={t(chatId ? 'memory.chatTitle' : 'memory.title', {name: title})} />
            <Dialog.Body>
                {!document && !memory.error ? (
                    <Flex
                        direction="column"
                        gap={4}
                        role="status"
                        aria-label={t('loading')}
                        qa="memory-skeleton"
                    >
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
                            <Text color="secondary">
                                {t(chatId ? 'memory.chatHint' : 'memory.globalHint')}
                            </Text>
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
                                    <Text>{document.summary || t('memory.empty')}</Text>
                                </Box>
                                <Text color="secondary" className={styles.hint}>
                                    {t(document.backfillComplete ? 'memory.complete' : 'memory.scanning')}
                                </Text>
                            </Box>
                            <Flex gap={2} wrap>
                                <Button
                                    disabled={memory.busy || document.status === 'disabled'}
                                    onClick={() => void memory.refresh()}
                                >
                                    {t('memory.refresh')}
                                </Button>
                                {chatId && (
                                    <Button
                                        disabled={memory.busy || document.status === 'disabled'}
                                        onClick={() => void memory.refresh(true)}
                                    >
                                        {t('memory.rebuild')}
                                    </Button>
                                )}
                                <Button
                                    view="flat-secondary"
                                    disabled={memory.busy}
                                    onClick={() => void memory.reload()}
                                >
                                    {t('memory.reload')}
                                </Button>
                            </Flex>
                            {document.error && <Text color="danger">{document.error}</Text>}
                            <Text color="secondary" className={styles.path}>
                                {document.relativePath}
                            </Text>
                        </Flex>
                    )
                )}
                {memory.error && (
                    <Flex direction="column" gap={3}>
                        <Text color="danger">{memory.error}</Text>
                        {!document && <Button onClick={() => void memory.reload()}>{t('retry')}</Button>}
                    </Flex>
                )}
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={t('memory.save')}
                textButtonCancel={t('close')}
                onClickButtonApply={() => void memory.save()}
                onClickButtonCancel={onClose}
                loading={memory.busy}
                propsButtonApply={{disabled: !document || memory.notes === document.notes}}
            />
        </Dialog>
    );
}
