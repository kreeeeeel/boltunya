import {Dialog, Flex, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';

export function GlobalPromptDialog({
    value,
    onClose,
    onRefresh,
}: {
    readonly value: string;
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations();
    const [prompt, setPrompt] = useState(value);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    async function save() {
        setBusy(true);
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: {globalPrompt: prompt}}));
            await onRefresh();
            onClose();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Dialog
            contentOverflow="auto"
            open
            onClose={onClose}
            maxWidth="s"
            fullWidth
            aria-label={t('globalPrompt')}
        >
            <Dialog.Header caption={t('globalPrompt')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Text color="secondary">{t('globalPromptHint')}</Text>
                    <TextArea
                        size="l"
                        value={prompt}
                        onUpdate={setPrompt}
                        minRows={8}
                        maxRows={14}
                        controlProps={{'aria-label': t('globalPrompt'), maxLength: 4000}}
                    />
                    <Text color="secondary">{t('promptCount', {count: prompt.length})}</Text>
                    {error && <Text color="danger">{error}</Text>}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={t('save')}
                textButtonCancel={t('cancel')}
                onClickButtonApply={() => void save()}
                onClickButtonCancel={onClose}
                loading={busy}
            />
        </Dialog>
    );
}
