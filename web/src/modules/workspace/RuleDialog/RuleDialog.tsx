import {
    Box,
    Button,
    Dialog,
    Flex,
    Icon,
    Label,
    Select,
    Skeleton,
    Slider,
    Switch,
    Text,
    TextArea,
} from '@gravity-ui/uikit';
import {Sparkles} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';

import type {RuleDialogProps} from '@/modules/workspace/RuleDialog/RuleDialog.types';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Reply, ChatRule} from '@/shared/api/client';
import {ReplySkeleton} from '@/shared/ui/ReplySkeleton/ReplySkeleton';
import {communicationStyles} from '@/modules/workspace/model/communicationStyles';
import {StyleOption} from '@/modules/workspace/StyleOption/StyleOption';
import {ChatHistoryActions} from '@/modules/workspace/ChatHistoryActions';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';

export function RuleDialog({
    chat,
    engine,
    customStyles,
    defaultModel,
    onClose,
    onCollectContext,
    onDeleted,
    onSave,
}: RuleDialogProps) {
    const t = useTranslations();
    const [rule, setRule] = useState(chat.rule);
    const [test, setTest] = useState('');
    const [result, setResult] = useState<Reply | null>(null);
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const feedback = useSaveFeedback(rule);

    async function save() {
        setBusy('save');
        setError('');
        feedback.clearSaved();
        try {
            await onSave(rule);
            feedback.markSaved();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }

    async function preview() {
        setBusy('test');
        setError('');
        try {
            setResult(
                dataOrThrow(
                    await api.POST('/api/preview', {
                        body: {text: test || t('testPlaceholder'), rule, chatId: chat.id},
                    }),
                ),
            );
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }

    return (
        <Dialog
            modalClassName={styles.modal}
            className={styles.dialog}
            qa="communication-dialog"
            contentOverflow="auto"
            open
            onClose={onClose}
            maxWidth="s"
            fullWidth
            aria-label={t('ruleTitle', {name: chat.title})}
        >
            <Dialog.Header caption={t('ruleTitle', {name: chat.title})} />
            <Dialog.Body>
                <Flex direction="column" gap={5} className={styles.body}>
                    <Text color="secondary">{t('ruleHint')}</Text>
                    <Switch
                        checked={rule.useMemory ?? true}
                        onUpdate={(useMemory) => setRule({...rule, useMemory})}
                    >
                        {t('memory.useChat')}
                    </Switch>
                    <ChatHistoryActions
                        chatId={chat.id}
                        disabled={!!busy}
                        onCollectContext={onCollectContext}
                        onDeleted={onDeleted}
                    />
                    <Flex className={styles.enable} justifyContent="space-between" alignItems="center">
                        <Flex gap={2} alignItems="center">
                            <Icon data={Sparkles} />
                            <Text variant="subheader-2">{t('enableAi')}</Text>
                        </Flex>
                        <Switch
                            checked={rule.enabled}
                            onUpdate={(enabled) => setRule({...rule, enabled})}
                            controlProps={{'aria-label': t('enableAi')}}
                        />
                    </Flex>
                    {chat.kind && chat.kind !== 'private' && (
                        <Box>
                            <Text className={styles.label}>{t('groupReplies')}</Text>
                            <Select
                                size="l"
                                width="max"
                                value={[rule.groupReplies ?? 'inherit']}
                                options={['inherit', 'disabled', 'mentions', 'all'].map((value) => ({
                                    value,
                                    content: t('groupMode.' + value),
                                }))}
                                onUpdate={([groupReplies]) =>
                                    setRule({...rule, groupReplies: groupReplies as ChatRule['groupReplies']})
                                }
                                aria-label={t('groupReplies')}
                            />
                        </Box>
                    )}
                    <Box>
                        <Text className={styles.label}>{t('style')}</Text>
                        <Select
                            width="max"
                            size="l"
                            controlClassName={styles.styleControl}
                            renderOption={(option) => <>{option.content}</>}
                            renderSelectedOption={(option) => <>{option.content}</>}
                            value={[rule.styleId ? 'preset:' + rule.styleId : rule.style]}
                            options={[
                                ...communicationStyles.map(({value, emoji}) => ({
                                    value,
                                    text: t(value),
                                    content: <StyleOption emoji={emoji} name={t(value)} />,
                                })),
                                ...customStyles.map((style) => ({
                                    value: 'preset:' + style.id,
                                    text: style.name,
                                    content: <StyleOption emoji={style.emoji} name={style.name} />,
                                })),
                            ]}
                            onUpdate={(value) => {
                                if (value[0]?.startsWith('preset:')) {
                                    setRule({...rule, style: 'custom', styleId: value[0].slice(7)});
                                    return;
                                }
                                const style = communicationStyles.find(
                                    (option) => option.value === value[0],
                                )?.value;
                                if (style) {
                                    setRule({...rule, style, styleId: ''});
                                }
                            }}
                            aria-label={t('style')}
                        />
                    </Box>
                    <Box>
                        <Text className={styles.label}>{t('model')}</Text>
                        {!engine ? (
                            <Skeleton height={36} />
                        ) : (
                            <Select
                                width="max"
                                size="l"
                                value={[(rule.useDefaultModel ?? true) ? 'default' : rule.model]}
                                options={[
                                    {
                                        value: 'default',
                                        content: t('modelDefaultOption', {
                                            name:
                                                engine.models.find((model) => model.id === defaultModel)
                                                    ?.name ?? defaultModel,
                                        }),
                                    },
                                    ...(engine?.models ?? []).map((model) => ({
                                        value: model.id,
                                        content:
                                            model.name + (model.installed ? '' : ' · ' + t('notInstalled')),
                                    })),
                                ]}
                                onUpdate={([model]) =>
                                    setRule({
                                        ...rule,
                                        model: model === 'default' ? defaultModel : model,
                                        useDefaultModel: model === 'default',
                                    })
                                }
                                aria-label={t('model')}
                            />
                        )}
                        <Text className={styles.hint}>{t('modelDefaultHint')}</Text>
                    </Box>
                    <Box>
                        <Text className={styles.label}>{t('instructions')}</Text>
                        <TextArea
                            value={rule.instructions}
                            onUpdate={(instructions) => setRule({...rule, instructions})}
                            minRows={3}
                            maxRows={5}
                            controlProps={{'aria-label': t('instructions'), maxLength: 4000}}
                        />
                    </Box>
                    <Box>
                        <Switch
                            checked={rule.matchMyStyle ?? true}
                            onUpdate={(matchMyStyle) => setRule({...rule, matchMyStyle})}
                        >
                            {t('matchMyStyle')}
                        </Switch>
                        <Text className={styles.hint}>{t('matchMyStyleHint')}</Text>
                    </Box>
                    <Box>
                        <Flex justifyContent="space-between" className={styles.label}>
                            <Text>{t('depth')}</Text>
                            <Label theme="info">{t('messagesCount', {count: rule.contextMessages})}</Label>
                        </Flex>
                        <Slider
                            value={rule.contextMessages}
                            min={4}
                            max={60}
                            onUpdate={(contextMessages) => setRule({...rule, contextMessages})}
                            aria-label={t('depth')}
                        />
                        <Text className={styles.hint}>{t('contextNote')}</Text>
                    </Box>
                    <Box qa="response-length-setting">
                        <Flex className={styles.label} justifyContent="space-between">
                            <Text>{t('length')}</Text>
                            <Text color="secondary">{rule.maxTokens}</Text>
                        </Flex>
                        <Slider
                            value={rule.maxTokens}
                            min={32}
                            max={512}
                            step={32}
                            onUpdate={(maxTokens) => setRule({...rule, maxTokens})}
                            aria-label={t('length')}
                        />
                    </Box>
                    <Box qa="reply-delay-setting">
                        <Flex className={styles.label} justifyContent="space-between">
                            <Text>{t('delay')}</Text>
                            <Text color="secondary">{t('seconds', {count: rule.delaySeconds})}</Text>
                        </Flex>
                        <Slider
                            value={rule.delaySeconds}
                            min={0}
                            max={60}
                            onUpdate={(delaySeconds) => setRule({...rule, delaySeconds})}
                            aria-label={t('delay')}
                        />
                    </Box>
                    <Box>
                        <Flex className={styles.label} justifyContent="space-between">
                            <Text>{t('creativity')}</Text>
                            <Text color="secondary">{rule.creativity}%</Text>
                        </Flex>
                        <Slider
                            value={rule.creativity}
                            min={0}
                            max={100}
                            onUpdate={(creativity) => setRule({...rule, creativity})}
                            aria-label={t('creativity')}
                        />
                    </Box>
                    <Switch checked={rule.emoji} onUpdate={(emoji) => setRule({...rule, emoji})}>
                        {t('emoji')}
                    </Switch>
                    <Box>
                        <Switch
                            checked={rule.internetAccess ?? true}
                            onUpdate={(internetAccess) => setRule({...rule, internetAccess})}
                        >
                            {t('internetAccess')}
                        </Switch>
                        <Text className={styles.hint}>{t('internetAccessHint')}</Text>
                    </Box>
                    <Box>
                        <Text className={styles.label}>{t('internetSearch.linksTitle')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[rule.sourceLinks ?? 'requested']}
                            options={['requested', 'always', 'never'].map((value) => ({
                                value,
                                content: t('internetSearch.modes.' + value),
                            }))}
                            onUpdate={([sourceLinks]) =>
                                setRule({...rule, sourceLinks: sourceLinks as ChatRule['sourceLinks']})
                            }
                            disabled={!(rule.internetAccess ?? true)}
                            aria-label={t('internetSearch.linksTitle')}
                        />
                        <Text className={styles.hint}>{t('internetSearch.linksHint')}</Text>
                    </Box>
                    <Flex direction="column" gap={3} className={styles.test}>
                        <Text variant="subheader-2">{t('preview')}</Text>
                        <Text className={styles.hint}>{t('previewHint')}</Text>
                        <TextArea
                            value={test}
                            onUpdate={setTest}
                            minRows={2}
                            placeholder={t('testPlaceholder')}
                            controlProps={{'aria-label': t('testMessage'), maxLength: 4096}}
                        />
                        <Button
                            view="outlined-info"
                            onClick={() => void preview()}
                            loading={busy === 'test'}
                            disabled={
                                !!busy ||
                                !engine?.models.some(
                                    (model) =>
                                        model.id ===
                                            ((rule.useDefaultModel ?? true) ? defaultModel : rule.model) &&
                                        model.installed,
                                )
                            }
                        >
                            <Icon data={Sparkles} />
                            {t('test')}
                        </Button>
                        {busy === 'test' && (
                            <Box className={styles.result}>
                                <ReplySkeleton />
                            </Box>
                        )}
                        {result && busy !== 'test' && (
                            <Box className={styles.result}>
                                <Text>{result.text}</Text>
                                <Text className={styles.hint}>
                                    {t('latency', {
                                        ms: result.durationMs,
                                        speed: result.tokensPerSecond.toFixed(1),
                                    })}
                                </Text>
                            </Box>
                        )}
                    </Flex>
                    {error && <Text color="danger">{error}</Text>}
                </Flex>
            </Dialog.Body>
            <SaveDialogFooter
                saved={feedback.saved}
                saveLabel={t('save')}
                cancelLabel={t('cancel')}
                onClose={onClose}
                onSave={() => void save()}
                busy={busy === 'save'}
                disabled={!!busy}
            />
        </Dialog>
    );
}
