import {Microphone, Volume} from '@gravity-ui/icons';
import {Flex, Icon, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {AppState, ChatRule} from '@/shared/api/client';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {voicePreferences} from '@/modules/speech/model/voicePreferences';
import styles from '@/modules/speech/VoiceSummary/VoiceSummary.module.css';

export function VoiceSummary({
    settings,
    rule,
}: {
    readonly settings: AppState['settings'];
    readonly rule: ChatRule;
}) {
    const t = useTranslations('voice');
    const speech = useSpeechState();
    const preferences = voicePreferences(settings, rule);
    function title(id: string) {
        return speech.state?.assets.find((asset) => asset.id === id)?.title ?? id;
    }

    return (
        <Flex direction="column" gap={3} qa="chat-voice-summary">
            <Flex direction="column" gap={1}>
                <Text variant="subheader-1">{t('summaryTitle')}</Text>
                <Text color="secondary">
                    {t(rule.voiceSettingsMode === 'override' ? 'chatOverride' : 'chatInherited')}
                </Text>
            </Flex>
            <Flex direction="column" gap={2}>
                <Flex gap={2} alignItems="center">
                    <Icon data={Volume} size={17} />
                    <Text className={styles.value}>{title(preferences.voiceSynthesisModel)}</Text>
                </Flex>
                <Text color="secondary">
                    {t('replyModes.' + preferences.voiceReplyMode)} · {preferences.voiceSpeechRate.toFixed(1)}
                    ×
                </Text>
            </Flex>
            <Flex direction="column" gap={2}>
                <Flex gap={2} alignItems="center">
                    <Icon data={Microphone} size={17} />
                    <Text className={styles.value}>{title(preferences.voiceRecognitionModel)}</Text>
                </Flex>
                <Text color="secondary">
                    {t(preferences.voiceInputEnabled ? 'recognitionOn' : 'recognitionOff')}
                </Text>
            </Flex>
        </Flex>
    );
}
