import {Flex, Icon, Select, Switch, Text} from '@gravity-ui/uikit';
import {Sparkles} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import type {ChatRule} from '@/shared/api/client';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

export function RuleReplyFields({value, onUpdate}: RuleFieldsProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <Flex className={styles.enable} alignItems="center" justifyContent="space-between" gap={3}>
                <Flex alignItems="center" gap={2}>
                    <Icon data={Sparkles} size={18} />
                    <Text variant="subheader-2">{t('enableAi')}</Text>
                </Flex>
                <Switch
                    checked={value.enabled}
                    onUpdate={(enabled) => onUpdate({...value, enabled})}
                    controlProps={{'aria-label': t('enableAi')}}
                />
            </Flex>
            <Flex direction="column" gap={2}>
                <Text>{t('replyInterruption.mode')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.replyInterruptionMode ?? 'inherit']}
                    options={(['inherit', 'replace', 'finish', 'each'] as const).map((mode) => ({
                        value: mode,
                        content: t('replyInterruption.options.' + mode),
                    }))}
                    aria-label={t('replyInterruption.mode')}
                    onUpdate={([mode]) => {
                        if (
                            mode === 'inherit' ||
                            mode === 'replace' ||
                            mode === 'finish' ||
                            mode === 'each'
                        ) {
                            onUpdate({...value, replyInterruptionMode: mode});
                        }
                    }}
                />
                <Text color="secondary">
                    {t('replyInterruption.hints.' + (value.replyInterruptionMode ?? 'inherit'))}
                </Text>
            </Flex>
            <Flex direction="column" gap={2}>
                <Text>{t('chatFeatures.delivery')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.deliveryMode ?? 'inherit']}
                    options={['inherit', 'reply', 'send'].map((mode) => ({
                        value: mode,
                        content: t('settingsCenter.delivery.' + mode),
                    }))}
                    aria-label={t('chatFeatures.delivery')}
                    onUpdate={([deliveryMode]) =>
                        onUpdate({...value, deliveryMode: deliveryMode as ChatRule['deliveryMode']})
                    }
                />
            </Flex>
            <SettingsSlider
                qa="response-length-setting"
                label={t('length')}
                displayValue={value.maxTokens}
                value={value.maxTokens}
                min={32}
                max={512}
                step={32}
                onUpdate={(maxTokens) => onUpdate({...value, maxTokens})}
            />
            <SettingsSlider
                qa="reply-delay-setting"
                label={t('delay')}
                displayValue={t('seconds', {count: value.delaySeconds})}
                value={value.delaySeconds}
                min={0}
                max={60}
                onUpdate={(delaySeconds) => onUpdate({...value, delaySeconds})}
            />
        </Flex>
    );
}
