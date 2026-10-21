import {Button, Dialog, Flex, Skeleton, Text} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, errorMessage, type Message} from '@/shared/api/client';
import {MessageBubble} from '@/modules/workspace/Conversation/MessageBubble';
import styles from '@/modules/workspace/DeletedMessagesDialog/DeletedMessagesDialog.module.css';
export function DeletedMessagesDialog({
    chatId,
    title,
    onClose,
}: {
    readonly chatId: string;
    readonly title: string;
    readonly onClose: () => void;
}) {
    const t = useTranslations();
    const [messages, setMessages] = useState<Message[]>();
    const [busy, setBusy] = useState(false);
    const [more, setMore] = useState(false);
    const [error, setError] = useState('');
    useEffect(() => {
        let active = true;
        void api.chats
            .getDeletedMessages({
                chatId,
            })
            .then((value) => {
                if (active) {
                    setMessages(value);
                    setMore(value.length === 100);
                }
            })
            .catch((cause) => {
                if (active) {
                    setError(errorMessage(cause));
                }
            });
        return () => {
            active = false;
        };
    }, [chatId]);
    async function earlier() {
        setBusy(true);
        setError('');
        try {
            const value = await api.chats.getDeletedMessages({
                chatId,
                before: messages?.[0]?.id ?? '',
            });
            setMessages((previous) => [...value, ...(previous ?? [])]);
            setMore(value.length === 100);
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Dialog
            open
            maxWidth="s"
            fullWidth
            disableHeightTransition
            contentOverflow="auto"
            onClose={onClose}
            aria-label={t('deleted.title')}
        >
            <Dialog.Header caption={t('deleted.title')} />
            <Dialog.Body>
                <Flex direction="column" gap={3}>
                    <Text variant="subheader-2">{title}</Text>
                    <Text color="secondary">{t('deleted.hint')}</Text>
                    {!messages && !error && (
                        <Flex direction="column" gap={3} role="status" aria-label={t('loading')}>
                            {[76, 56, 76].map((height, index) => (
                                <Skeleton key={index} height={height} width={index === 1 ? '80%' : '100%'} />
                            ))}
                        </Flex>
                    )}
                    {more && (
                        <Button loading={busy} onClick={() => void earlier()}>
                            {t('deleted.earlier')}
                        </Button>
                    )}
                    {messages?.length === 0 && <Text>{t('deleted.empty')}</Text>}
                    <Flex direction="column" gap={3} className={styles.messages}>
                        {messages?.map((message) => (
                            <MessageBubble key={message.id} message={message} />
                        ))}
                    </Flex>
                    {error && <Text color="danger">{error}</Text>}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer>
                <Button onClick={onClose}>{t('close')}</Button>
            </Dialog.Footer>
        </Dialog>
    );
}
