import {Flex, Select, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

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
                <SettingsSlider
                    label={t('probability')}
                    displayValue={value.voiceReplyProbability + '%'}
                    min={0}
                    max={100}
                    step={1}
                    value={value.voiceReplyProbability}
                    onUpdate={(voiceReplyProbability) => onUpdate({...value, voiceReplyProbability})}
                />
            )}
        </Flex>
    );
}
