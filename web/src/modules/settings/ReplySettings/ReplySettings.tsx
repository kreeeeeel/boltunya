import {Box, Flex, Select, Slider, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import type {SettingsDraft} from '@/modules/settings/model/settingsDraft';
import {VoiceReplySettings} from '@/modules/settings/VoiceReplySettings/VoiceReplySettings';

export function ReplySettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <VoiceReplySettings value={value} onUpdate={onUpdate} />
            <Text color="secondary">{t('queueHint')}</Text>
            {(['replyDelaySeconds', 'replyIntervalSeconds'] as const).map((key) => (
                <Flex direction="column" gap={3} key={key}>
                    <Flex justifyContent="space-between">
                        <Text>{t(key)}</Text>
                        <Text color="secondary">{t('seconds', {count: value[key]})}</Text>
                    </Flex>
                    <Slider
                        min={0}
                        max={120}
                        value={value[key]}
                        aria-label={t(key)}
                        onUpdate={(next) => onUpdate({...value, [key]: next})}
                    />
                </Flex>
            ))}
            <Switch checked={value.showTyping} onUpdate={(showTyping) => onUpdate({...value, showTyping})}>
                {t('showTyping')}
            </Switch>
            <Flex direction="column" gap={2}>
                <Text>{t('replyInterruption.title')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.replyProcessingMode]}
                    options={(['replace', 'finish', 'each'] as const).map((mode) => ({
                        value: mode,
                        content: t('replyInterruption.options.' + mode),
                    }))}
                    aria-label={t('replyInterruption.title')}
                    onUpdate={([replyProcessingMode]) => {
                        if (
                            replyProcessingMode === 'replace' ||
                            replyProcessingMode === 'finish' ||
                            replyProcessingMode === 'each'
                        ) {
                            onUpdate({...value, replyProcessingMode});
                        }
                    }}
                />
                <Text color="secondary">{t('replyInterruption.hints.' + value.replyProcessingMode)}</Text>
            </Flex>
            <Flex direction="column" gap={2}>
                <Switch
                    checked={value.formatReplies}
                    onUpdate={(formatReplies) => onUpdate({...value, formatReplies})}
                >
                    {t('formatReplies')}
                </Switch>
                <Text color="secondary">{t('formatRepliesHint')}</Text>
            </Flex>
            <Box>
                <Switch
                    checked={value.ignoreAssistantTasks}
                    onUpdate={(ignoreAssistantTasks) => onUpdate({...value, ignoreAssistantTasks})}
                >
                    {t('ignoreAssistantTasks')}
                </Switch>
                <Text as="p" color="secondary">
                    {t('ignoreAssistantTasksHint')}
                </Text>
            </Box>
            <Flex direction="column" gap={3}>
                <Text>{t('groupReplies')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.groupReplies]}
                    options={['disabled', 'mentions', 'all'].map((mode) => ({
                        value: mode,
                        content: t('groupMode.' + mode),
                    }))}
                    onUpdate={([groupReplies]) =>
                        onUpdate({...value, groupReplies: groupReplies as SettingsDraft['groupReplies']})
                    }
                    aria-label={t('groupReplies')}
                />
                <Text color="secondary">{t('groupRepliesHint')}</Text>
            </Flex>
            <Flex direction="column" gap={3}>
                <Text>{t('settingsCenter.deliveryMode')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.deliveryMode]}
                    options={['reply', 'send'].map((mode) => ({
                        value: mode,
                        content: t('settingsCenter.delivery.' + mode),
                    }))}
                    onUpdate={([deliveryMode]) =>
                        onUpdate({...value, deliveryMode: deliveryMode as SettingsDraft['deliveryMode']})
                    }
                    aria-label={t('settingsCenter.deliveryMode')}
                />
            </Flex>
        </Flex>
    );
}
