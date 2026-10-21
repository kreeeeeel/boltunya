import {Dialog, Flex, Loader, RadioGroup, Text} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';
import type {
    MessageDeletionDialogProps,
    MessageDeletionResult,
} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';

type Scope = components['schemas']['MessageDeletionRequest']['scope'];

export function MessageDeletionDialog({chatId, messageId, onClose, onDeleted}: MessageDeletionDialogProps) {
    const t = useTranslations('messageDelete');
    const common = useTranslations();
    const [options, setOptions] = useState<components['schemas']['MessageDeletionOptions'] | null>(null);
    const [scope, setScope] = useState<Scope>('self');
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [deleted, setDeleted] = useState<MessageDeletionResult | null>(null);
    useEffect(() => {
        const controller = new AbortController();
        async function load() {
            try {
                const permissions = dataOrThrow(
                    await api.GET('/api/chats/{chatId}/messages/{messageId}/deletion', {
                        params: {path: {chatId, messageId}},
                        signal: controller.signal,
                    }),
                );
                if (!controller.signal.aborted) {
                    setOptions(permissions);
                    setScope(permissions.canDeleteForSelf ? 'self' : 'everyone');
                }
            } catch (cause) {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            } finally {
                if (!controller.signal.aborted) {
                    setLoading(false);
                }
            }
        }
        void load();
        return () => controller.abort();
    }, [chatId, messageId]);

    async function remove() {
        if (busy || !options || deleted) {
            return;
        }
        setBusy(true);
        setError('');
        let result: MessageDeletionResult;
        try {
            result = dataOrThrow(
                await api.DELETE('/api/chats/{chatId}/messages/{messageId}', {
                    params: {path: {chatId, messageId}},
                    body: {scope},
                }),
            );
            setDeleted(result);
        } catch (cause) {
            setError(errorMessage(cause));
            setBusy(false);
            return;
        }
        try {
            await onDeleted?.(result);
            onClose();
        } catch {
            setError(t('refreshFailed'));
        } finally {
            setBusy(false);
        }
    }

    const available = options?.canDeleteForSelf || options?.canDeleteForEveryone;
    return (
        <Dialog
            open
            onClose={() => !busy && onClose()}
            maxWidth="s"
            aria-label={t('title')}
            returnFocus={false}
            disableOutsideClick={busy}
        >
            <Dialog.Header caption={deleted ? t('completed') : t('title')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Text color="secondary">{t('description')}</Text>
                    {loading ? (
                        <Flex alignItems="center" gap={3} role="status">
                            <Loader size="s" />
                            <Text>{t('loading')}</Text>
                        </Flex>
                    ) : available ? (
                        !deleted && (
                            <RadioGroup
                                direction="vertical"
                                size="l"
                                aria-label={t('scope')}
                                value={scope}
                                disabled={busy}
                                onUpdate={(value) => setScope(value as Scope)}
                                options={[
                                    ...(options?.canDeleteForSelf
                                        ? [{value: 'self', content: t('self')}]
                                        : []),
                                    ...(options?.canDeleteForEveryone
                                        ? [{value: 'everyone', content: t('everyone')}]
                                        : []),
                                ]}
                            />
                        )
                    ) : (
                        !error && <Text>{t('unavailable')}</Text>
                    )}
                    {error && (
                        <Text color="danger" role="alert">
                            {error}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonCancel={common(deleted ? 'close' : 'cancel')}
                onClickButtonCancel={onClose}
                propsButtonCancel={{disabled: busy}}
                textButtonApply={
                    !deleted && available
                        ? t(scope === 'self' ? 'confirmSelf' : 'confirmEveryone')
                        : undefined
                }
                onClickButtonApply={() => void remove()}
                propsButtonApply={{view: 'outlined-danger', disabled: busy || loading || !available}}
                loading={busy}
            />
        </Dialog>
    );
}
