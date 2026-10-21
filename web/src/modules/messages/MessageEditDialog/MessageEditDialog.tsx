import {Button, Dialog, Flex, Loader, Text} from '@gravity-ui/uikit';
import {lazy, Suspense, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {Message} from '@/shared/api/client';
import {useMessageEditSession} from '@/modules/messages/MessageEditDialog/useMessageEditSession';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import styles from '@/modules/messages/MessageEditDialog/MessageEditDialog.module.css';
import {messageEditLength} from '@/modules/messages/MessageEditDialog/messageEditDraft';

const MessageEditEditor = lazy(() =>
    import('@/modules/messages/MessageEditEditor/MessageEditEditor').then((module) => ({
        default: module.MessageEditEditor,
    })),
);

export function MessageEditDialog({
    message,
    onUpdated,
    onClose,
}: {
    readonly message: Message;
    readonly onUpdated: (message: Message) => void;
    readonly onClose: () => void;
}) {
    const t = useTranslations('messageEdit');
    const session = useMessageEditSession(message.chatId, message.id, onUpdated);
    const [confirmation, setConfirmation] = useState<'close' | 'reload' | null>(null);
    const caption = session.options?.kind === 'caption' || message.kind !== 'text';
    const title = t(caption ? 'titleCaption' : 'titleText');
    const disabled =
        session.busy || session.refreshing || session.needsRefresh || session.conflict || session.unavailable;

    function request(action: 'close' | 'reload') {
        if (session.busy) {
            return;
        }
        if (session.dirty && !session.needsRefresh) {
            setConfirmation(action);
        } else if (action === 'close') {
            onClose();
        } else {
            void session.reload();
        }
    }

    return (
        <>
            <Dialog
                open
                maxWidth="m"
                aria-label={title}
                qa="message-edit-dialog"
                returnFocus={false}
                disableOutsideClick={session.busy || confirmation !== null}
                disableEscapeKeyDown={session.busy || confirmation !== null}
                onClose={() => request('close')}
                onEnterKeyDown={(event) => {
                    if ((event.ctrlKey || event.metaKey) && !event.isComposing) {
                        event.preventDefault();
                        void session.save();
                    }
                }}
            >
                <Dialog.Header caption={title} />
                <Dialog.Body>
                    <Flex direction="column" gap={4} className={styles.body}>
                        {session.loading && (
                            <Flex alignItems="center" gap={3} role="status">
                                <Loader size="s" />
                                <Text>{t('loading')}</Text>
                            </Flex>
                        )}
                        {session.initial && session.options && (
                            <>
                                <Suspense
                                    fallback={
                                        <Flex alignItems="center" gap={3} role="status">
                                            <Loader size="s" />
                                            <Text>{t('loading')}</Text>
                                        </Flex>
                                    }
                                >
                                    <MessageEditEditor
                                        key={session.version}
                                        initialDraft={session.initial}
                                        draftId={`${message.chatId}:${message.id}:${session.options.revision}:${session.version}`}
                                        onChange={session.setDraft}
                                        onError={session.setEditorError}
                                        disabled={disabled}
                                        autoFocus
                                        ariaLabel={title}
                                    />
                                </Suspense>
                                <Text color={session.validation ? 'danger' : 'secondary'}>
                                    {session.validation ||
                                        `${messageEditLength(session.draft?.text ?? '')} / ${session.options.maxLength}`}
                                </Text>
                            </>
                        )}
                        {session.refreshing && !session.loading && (
                            <Flex alignItems="center" gap={3} role="status">
                                <Loader size="s" />
                                <Text>{t('loading')}</Text>
                            </Flex>
                        )}
                        {session.editorError && (
                            <Text color="danger" role="alert">
                                {t('formattingUnavailable')}
                            </Text>
                        )}
                        {session.needsRefresh && session.readError ? (
                            <Flex direction="column" gap={3} className={styles.notice} role="status">
                                <Text>{t('refreshFailedAfterSave')}</Text>
                                <Button
                                    view="flat-info"
                                    disabled={session.refreshing}
                                    onClick={() => void session.reload()}
                                >
                                    {t('retryRefresh')}
                                </Button>
                            </Flex>
                        ) : session.readError ? (
                            <Flex direction="column" gap={3} className={styles.notice}>
                                <Text color="danger" role="alert">
                                    {session.readError}
                                </Text>
                                <Button
                                    view="flat-info"
                                    disabled={session.refreshing}
                                    onClick={() => request('reload')}
                                >
                                    {t('reload')}
                                </Button>
                            </Flex>
                        ) : null}
                        {session.saveError && (
                            <Text color="danger" role="alert">
                                {session.conflict ? t('changedElsewhere') : session.saveError}
                            </Text>
                        )}
                        {(session.conflict || session.unavailable || session.editorError) && (
                            <Button view="flat-info" onClick={() => request('reload')}>
                                {t('reload')}
                            </Button>
                        )}
                    </Flex>
                </Dialog.Body>
                <SaveDialogFooter
                    saved={session.saved}
                    busy={session.busy}
                    disabled={
                        disabled ||
                        session.loading ||
                        !!session.editorError ||
                        !!session.validation ||
                        !session.dirty
                    }
                    saveLabel={t('save')}
                    cancelLabel={t('close')}
                    onSave={() => void session.save()}
                    onClose={() => request('close')}
                />
            </Dialog>
            {confirmation && (
                <Dialog
                    open
                    maxWidth="s"
                    aria-label={t('discardTitle')}
                    returnFocus={false}
                    onClose={() => setConfirmation(null)}
                >
                    <Dialog.Header caption={t('discardTitle')} />
                    <Dialog.Body>
                        <Text>{t('discardDescription')}</Text>
                    </Dialog.Body>
                    <Dialog.Footer
                        textButtonCancel={t('keepEditing')}
                        onClickButtonCancel={() => setConfirmation(null)}
                        textButtonApply={t('discard')}
                        propsButtonApply={{view: 'outlined-danger'}}
                        onClickButtonApply={() => {
                            const action = confirmation;
                            setConfirmation(null);
                            if (action === 'close') {
                                onClose();
                            } else {
                                void session.reload();
                            }
                        }}
                    />
                </Dialog>
            )}
        </>
    );
}
