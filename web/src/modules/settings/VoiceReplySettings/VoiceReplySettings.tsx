import {Flex, Select, Slider, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';

export function VoiceReplySettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations('voice');
    return (
        <Flex direction="column" gap={3}>
            <Text variant="subheader-2">{t('replyMode')}</Text>
            <Select
                size="l"
                width="max"
                value={[value.voiceReplyMode]}
                aria-label={t('replyMode')}
                options={(['text', 'always', 'random', 'mirror'] as const).map((mode) => ({
                    value: mode,
                    content: t('replyModes.' + mode),
                }))}
                onUpdate={([voiceReplyMode]) => {
                    if (
                        voiceReplyMode === 'text' ||
                        voiceReplyMode === 'always' ||
                        voiceReplyMode === 'random' ||
                        voiceReplyMode === 'mirror'
                    ) {
                        onUpdate({...value, voiceReplyMode});
                    }
                }}
            />
            <Text color="secondary">{t('replyHints.' + value.voiceReplyMode)}</Text>
            {value.voiceReplyMode === 'random' && (
                <Flex direction="column" gap={2}>
                    <Flex justifyContent="space-between" gap={3}>
                        <Text>{t('probability')}</Text>
                        <Text color="secondary">{value.voiceReplyProbability}%</Text>
                    </Flex>
                    <Slider
                        min={0}
                        max={100}
                        step={1}
                        value={value.voiceReplyProbability}
                        aria-label={t('probability')}
                        onUpdate={(voiceReplyProbability) => onUpdate({...value, voiceReplyProbability})}
                    />
                </Flex>
            )}
        </Flex>
    );
}
