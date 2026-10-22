import {Flex, Select, Switch, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {ProactiveInterval} from '@/modules/settings/ProactiveInterval/ProactiveInterval';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';

export function RuleProactiveFields({value, onUpdate}: RuleFieldsProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={4}>
            <Flex direction="column" gap={2}>
                <Text>{t('proactive.mode')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.proactiveMode ?? 'inherit']}
                    aria-label={t('proactive.mode')}
                    options={(['inherit', 'disabled', 'enabled'] as const).map((mode) => ({
                        value: mode,
                        content: t('proactive.modes.' + mode),
                    }))}
                    onUpdate={([proactiveMode]) => {
                        if (
                            proactiveMode === 'inherit' ||
                            proactiveMode === 'disabled' ||
                            proactiveMode === 'enabled'
                        ) {
                            onUpdate({...value, proactiveMode});
                        }
                    }}
                />
                <Text color="secondary">{t('proactive.hint')}</Text>
            </Flex>
            <Switch
                checked={!value.proactiveIntervalMinutes}
                onUpdate={(inherit) => onUpdate({...value, proactiveIntervalMinutes: inherit ? 0 : 60})}
            >
                {t('proactive.inheritInterval')}
            </Switch>
            {!!value.proactiveIntervalMinutes && (
                <ProactiveInterval
                    value={value.proactiveIntervalMinutes}
                    disabled={value.proactiveMode === 'disabled'}
                    onUpdate={(proactiveIntervalMinutes) => onUpdate({...value, proactiveIntervalMinutes})}
                />
            )}
            <Flex direction="column" gap={2}>
                <Text>{t('proactive.messageMode')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.proactiveMessageMode ?? 'ai']}
                    options={(['ai', 'text'] as const).map((mode) => ({
                        value: mode,
                        content: t('proactive.messageModes.' + mode),
                    }))}
                    aria-label={t('proactive.messageMode')}
                    onUpdate={([proactiveMessageMode]) => {
                        if (proactiveMessageMode === 'ai' || proactiveMessageMode === 'text') {
                            onUpdate({...value, proactiveMessageMode});
                        }
                    }}
                />
            </Flex>
            {value.proactiveMessageMode === 'text' && (
                <Flex direction="column" gap={2}>
                    <Text>{t('proactive.messageText')}</Text>
                    <TextArea
                        value={value.proactiveText ?? ''}
                        onUpdate={(proactiveText) => onUpdate({...value, proactiveText})}
                        minRows={3}
                        maxRows={8}
                        controlProps={{'aria-label': t('proactive.messageText'), maxLength: 4096}}
                    />
                    <Text color="secondary">{t('proactive.messageTextHint')}</Text>
                </Flex>
            )}
        </Flex>
    );
}
