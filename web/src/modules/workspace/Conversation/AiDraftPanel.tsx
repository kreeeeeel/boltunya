import {PaperPlane, Xmark} from '@gravity-ui/icons';
import {Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import type {Text as OutgoingText} from '@aigram/api-client';
import {MessageDraftEditor} from '@/modules/messages/MessageDraftEditor/MessageDraftEditor';
import styles from '@/modules/workspace/Conversation/AiDraftPanel.module.css';

export function AiDraftPanel({
    draft,
    busy,
    error,
    onUpdate,
    onError,
    onSend,
    onDiscard,
}: {
    readonly draft: {id: string; message: OutgoingText};
    readonly busy: boolean;
    readonly error: string;
    readonly onUpdate: (message: OutgoingText) => void;
    readonly onError: () => void;
    readonly onSend: () => Promise<void>;
    readonly onDiscard: () => void;
}) {
    const t = useTranslations();
    const [ready, setReady] = useState(false);
    const tooLong = Array.from(draft.message.text).length > 4096;
    const sendDisabled = busy || !ready || !!error || tooLong || !draft.message.text.trim();

    return (
        <Flex direction="column" gap={3} className={styles.root} qa="ai-text-draft">
            <Flex alignItems="center" justifyContent="space-between" gap={3}>
                <Text variant="subheader-2">{t('assistantBar.draftTitle')}</Text>
                <Button
                    size="m"
                    view="flat-secondary"
                    disabled={busy}
                    aria-label={t('assistantBar.discardDraft')}
                    onClick={onDiscard}
                >
                    <Icon data={Xmark} size={18} />
                </Button>
            </Flex>
            <MessageDraftEditor
                initialMessage={draft.message}
                draftId={draft.id}
                ariaLabel={t('assistantBar.draftText')}
                disabled={busy || !!error}
                onChange={(message) => {
                    setReady(true);
                    onUpdate(message);
                }}
                onError={onError}
                onSubmit={sendDisabled ? undefined : onSend}
            />
            {(error || tooLong) && (
                <Text color="danger" role="alert">
                    {error || t('messageEdit.tooLong', {max: 4096})}
                </Text>
            )}
            <Flex justifyContent="flex-end">
                <Button
                    size="l"
                    view="action"
                    loading={busy}
                    disabled={sendDisabled}
                    onClick={() => void onSend()}
                    aria-label={t('send')}
                >
                    <Button.Icon>
                        <Icon data={PaperPlane} size={18} />
                    </Button.Icon>
                    {t('send')}
                </Button>
            </Flex>
        </Flex>
    );
}
