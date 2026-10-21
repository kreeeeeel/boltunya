import {Flex, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import type {Engine} from '@/shared/api/client';
import {ModelPromptEditor} from '@/modules/settings/ModelPromptEditor/ModelPromptEditor';

export function PromptSettings({
    value,
    onUpdate,
    engine,
    defaultModel,
    defaultProfileId,
}: SettingsDraftSectionProps & {
    readonly engine?: Engine;
    readonly defaultModel: string;
    readonly defaultProfileId: string;
}) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={3}>
            <Text color="secondary">{t('globalPromptHint')}</Text>
            <TextArea
                size="l"
                value={value.globalPrompt}
                onUpdate={(globalPrompt) => onUpdate({...value, globalPrompt})}
                minRows={7}
                maxRows={20}
                controlProps={{'aria-label': t('globalPrompt'), maxLength: 4000}}
            />
            <Text color="secondary">{t('promptCount', {count: value.globalPrompt.length})}</Text>
            <ModelPromptEditor
                engine={engine}
                defaultModel={defaultModel}
                defaultProfileId={defaultProfileId}
                globalPrompt={value.globalPrompt}
            />
        </Flex>
    );
}
