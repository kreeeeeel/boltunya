import {Flex, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';

export function PromptSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={3}>
            <Text color="secondary">{t('globalPromptHint')}</Text>
            <TextArea
                size="l"
                value={value.globalPrompt}
                onUpdate={(globalPrompt) => onUpdate({...value, globalPrompt})}
                minRows={12}
                maxRows={20}
                controlProps={{'aria-label': t('globalPrompt'), maxLength: 4000}}
            />
            <Text color="secondary">{t('promptCount', {count: value.globalPrompt.length})}</Text>
        </Flex>
    );
}
