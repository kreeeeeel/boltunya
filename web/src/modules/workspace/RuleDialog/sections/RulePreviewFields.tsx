import {Box, Button, Flex, Icon, Text, TextArea} from '@gravity-ui/uikit';
import {Sparkles} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import type {ChatRule, Reply} from '@/shared/api/client';
import {api, errorMessage} from '@/shared/api/client';
import {ReplySkeleton} from '@/shared/ui/ReplySkeleton/ReplySkeleton';
import {RichText} from '@/modules/workspace/RichText/RichText';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';

export function RulePreviewFields({
    rule,
    chatId,
    available,
    disabled,
    onBusyChange,
}: {
    readonly rule: ChatRule;
    readonly chatId: string;
    readonly available: boolean;
    readonly disabled: boolean;
    readonly onBusyChange: (busy: boolean) => void;
}) {
    const t = useTranslations();
    const [text, setText] = useState('');
    const [result, setResult] = useState<Reply | null>(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    async function preview() {
        setBusy(true);
        onBusyChange(true);
        setError('');
        try {
            setResult(
                await api.models.preview({preview: {text: text || t('testPlaceholder'), rule, chatId}}),
            );
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
            onBusyChange(false);
        }
    }

    return (
        <Flex direction="column" gap={4}>
            <TextArea
                value={text}
                onUpdate={setText}
                minRows={3}
                maxRows={8}
                controlProps={{'aria-label': t('testMessage'), maxLength: 4096}}
            />
            <Box>
                <Button
                    size="l"
                    view="outlined"
                    onClick={() => void preview()}
                    loading={busy}
                    disabled={disabled || !available}
                >
                    <Button.Icon>
                        <Icon data={Sparkles} size={18} />
                    </Button.Icon>
                    {t('test')}
                </Button>
            </Box>
            {busy && (
                <Box className={styles.result}>
                    <ReplySkeleton />
                </Box>
            )}
            {result && !busy && (
                <Flex direction="column" gap={2} className={styles.result}>
                    <RichText text={result.text} entities={result.entities} />
                    <Text color="secondary">
                        {t('latency', {ms: result.durationMs, speed: result.tokensPerSecond.toFixed(1)})}
                    </Text>
                </Flex>
            )}
            {error && (
                <Text color="danger" role="alert">
                    {error}
                </Text>
            )}
        </Flex>
    );
}
