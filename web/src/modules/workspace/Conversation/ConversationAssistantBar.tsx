import {BookOpen, Microphone, Sparkles, Text as TextIcon} from '@gravity-ui/icons';
import {Button, DropdownMenu, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechReady} from '@/modules/speech/model/speechReady';
import {VoiceComposeDialog} from '@/modules/speech/VoiceComposeDialog/VoiceComposeDialog';
import styles from '@/modules/workspace/Conversation/ConversationAssistantBar.module.css';

export function ConversationAssistantBar({
    chatId,
    modelLabel,
    busy,
    onDraft,
    onMemory,
    onMediaSent,
    voiceSynthesisModel = 'piper-ru-irina',
    voiceSpeechRate = 1,
    onVoiceSettings,
}: {
    readonly chatId: string;
    readonly modelLabel: string;
    readonly busy: boolean;
    readonly onDraft: (output: 'text' | 'voice') => Promise<void>;
    readonly onMemory: () => void;
    readonly onMediaSent: () => Promise<void>;
    readonly voiceSynthesisModel?: string;
    readonly voiceSpeechRate?: number;
    readonly onVoiceSettings?: () => void;
}) {
    const t = useTranslations();
    const [menuOpened, setMenuOpened] = useState(false);
    const [voiceOpened, setVoiceOpened] = useState(false);
    const speech = useSpeechState(menuOpened);
    const voiceReady = speechReady(speech.state?.assets ?? [], voiceSynthesisModel);

    return (
        <>
            <Flex alignItems="center" gap={3} className={styles.root} qa="conversation-assistant-bar">
                <Flex alignItems="center" justifyContent="center" className={styles.mark} aria-hidden="true">
                    <Icon data={Sparkles} size={20} />
                </Flex>
                <Flex alignItems="center" className={styles.model} gap={2}>
                    <Text color="secondary" className={styles.caption}>
                        {t('assistantBar.modelCaption')}
                    </Text>
                    <Text className={styles.modelName} title={modelLabel}>
                        {modelLabel}
                    </Text>
                </Flex>
                <Flex alignItems="center" gap={1} className={styles.actions}>
                    <Tooltip content={t('memory.openChat')}>
                        <Button
                            view="flat-secondary"
                            size="l"
                            className={styles.action}
                            aria-label={t('memory.openChat')}
                            onClick={onMemory}
                        >
                            <Icon data={BookOpen} size={19} />
                        </Button>
                    </Tooltip>
                    <Tooltip content={t('voice.manualTitle')}>
                        <Button
                            view="flat-secondary"
                            size="l"
                            className={styles.action}
                            aria-label={t('voice.manualTitle')}
                            disabled={busy}
                            onClick={() => setVoiceOpened(true)}
                        >
                            <Icon data={Microphone} size={20} />
                        </Button>
                    </Tooltip>
                    <DropdownMenu
                        onOpenToggle={(open) => {
                            if (open) {
                                setMenuOpened(true);
                            }
                        }}
                        items={[
                            {
                                text: t('voice.generateText'),
                                iconStart: <Icon data={TextIcon} size={18} />,
                                action: () => void onDraft('text'),
                            },
                            {
                                text: t('voice.generateVoice'),
                                iconStart: <Icon data={Microphone} size={18} />,
                                disabled: !voiceReady,
                                title: voiceReady ? undefined : t('voice.modelsRequired'),
                                action: () => void onDraft('voice'),
                            },
                            ...(!voiceReady && onVoiceSettings
                                ? [{text: t('voice.openSettings'), action: onVoiceSettings}]
                                : []),
                        ]}
                        popupProps={{placement: ['top-end', 'bottom-end'], className: styles.popup}}
                        renderSwitcher={(props) => (
                            <Tooltip content={t('draft')}>
                                <Button
                                    {...props}
                                    view="flat-secondary"
                                    size="l"
                                    className={styles.action}
                                    disabled={busy}
                                    aria-label={t('draft')}
                                >
                                    <Icon data={Sparkles} size={20} />
                                </Button>
                            </Tooltip>
                        )}
                    />
                </Flex>
            </Flex>
            {voiceOpened && (
                <VoiceComposeDialog
                    chatId={chatId}
                    defaultModelId={voiceSynthesisModel}
                    defaultSpeed={voiceSpeechRate}
                    onClose={() => setVoiceOpened(false)}
                    onSent={onMediaSent}
                    onSettings={onVoiceSettings}
                />
            )}
        </>
    );
}
