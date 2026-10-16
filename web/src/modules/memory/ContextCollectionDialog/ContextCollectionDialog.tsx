import {Dialog, Flex, Loader, RadioGroup, Text} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import {PanelSkeleton} from '@/shared/ui/PanelSkeleton/PanelSkeleton';
import styles from '@/modules/memory/ContextCollectionDialog/ContextCollectionDialog.module.css';
import {ContextCollectionStatus} from '@/modules/memory/ContextCollectionStatus/ContextCollectionStatus';

export function ContextCollectionDialog({
    chatId,
    title,
    onClose,
}: {
    readonly chatId: string;
    readonly title: string;
    readonly onClose: () => void;
}) {
    const t = useTranslations();
    const memory = useMemoryDocument(chatId);
    const global = chatId === '';
    const [mode, setMode] = useState('update');
    const [submitted, setSubmitted] = useState(false);
    const document = memory.document;
    const active = document?.collection?.active ?? false;
    const disabled = !document || document.status === 'disabled' || memory.busy || active;
    async function collect() {
        if (await memory.refresh(mode === 'rebuild', true)) {
            setSubmitted(true);
        }
    }
    return (
        <Dialog
            open
            maxWidth="s"
            fullWidth
            contentOverflow="auto"
            onClose={onClose}
            aria-label={t(global ? 'context.globalTitle' : 'context.title')}
        >
            <Dialog.Header caption={t(global ? 'context.globalTitle' : 'context.title')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Text variant="subheader-2">{title}</Text>
                    <Text color="secondary">{t(global ? 'context.globalHint' : 'context.hint')}</Text>
                    {!document && !memory.error ? (
                        <PanelSkeleton />
                    ) : (
                        document && (
                            <>
                                {active && document.collection ? (
                                    <ContextCollectionStatus
                                        progress={document.collection}
                                        busy={memory.busy}
                                        onCancel={() => void memory.cancel()}
                                    />
                                ) : (
                                    <RadioGroup
                                        direction="vertical"
                                        value={mode}
                                        onUpdate={setMode}
                                        disabled={memory.busy || (submitted && active)}
                                        aria-label={t('context.mode')}
                                        options={['update', 'rebuild'].map((value) => ({
                                            value,
                                            content: (
                                                <Flex direction="column" gap={1} className={styles.option}>
                                                    <Text>{t('context.' + value)}</Text>
                                                    <Text color="secondary">
                                                        {t(
                                                            global
                                                                ? value === 'update'
                                                                    ? 'context.globalUpdateHint'
                                                                    : 'context.globalRebuildHint'
                                                                : 'context.' + value + 'Hint',
                                                        )}
                                                    </Text>
                                                </Flex>
                                            ),
                                        }))}
                                    />
                                )}
                                {!active && (
                                    <Flex
                                        direction="column"
                                        gap={2}
                                        className={styles.status}
                                        role="status"
                                        aria-live="polite"
                                    >
                                        <Flex alignItems="center" gap={3}>
                                            {document.status === 'processing' && <Loader size="s" />}
                                            <Text>{t('memory.status.' + document.status)}</Text>
                                        </Flex>
                                        <Text color="secondary">
                                            {t('memory.scanned', {count: document.processedMessages})}
                                        </Text>
                                        {global && (
                                            <Text color="secondary">
                                                {t('memory.tracked', {count: document.trackedChats})}
                                            </Text>
                                        )}
                                        {document.status === 'disabled' && (
                                            <Text>{t('context.disabled')}</Text>
                                        )}
                                        {document.status === 'waiting_model' && (
                                            <Text>{t('context.waitingModel')}</Text>
                                        )}
                                        {submitted && active && (
                                            <Text color="secondary">
                                                {t(global ? 'context.globalRunning' : 'context.running')}
                                            </Text>
                                        )}
                                        {submitted && document.status === 'ready' && (
                                            <Text>{t('context.done')}</Text>
                                        )}
                                    </Flex>
                                )}
                                {document.error && <Text color="danger">{document.error}</Text>}
                            </>
                        )
                    )}
                    {memory.error && (
                        <Text color="danger" role="alert">
                            {memory.error}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={active ? undefined : t('context.start')}
                textButtonCancel={t('close')}
                onClickButtonApply={() => void collect()}
                onClickButtonCancel={onClose}
                loading={memory.busy}
                propsButtonApply={{disabled}}
            />
        </Dialog>
    );
}
