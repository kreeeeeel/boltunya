import {Flex, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';
import {ProactiveInterval} from '@/modules/settings/ProactiveInterval/ProactiveInterval';

export function ProactiveSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations('proactive');

    return (
        <Flex direction="column" gap={4}>
            <Switch
                checked={value.proactiveEnabled}
                onUpdate={(proactiveEnabled) => onUpdate({...value, proactiveEnabled})}
            >
                {t('title')}
            </Switch>
            <Text color="secondary">{t('hint')}</Text>
            <Flex direction="column" gap={2}>
                <Text>{t('interval')}</Text>
                <ProactiveInterval
                    value={value.proactiveIntervalMinutes}
                    disabled={!value.proactiveEnabled}
                    onUpdate={(proactiveIntervalMinutes) => onUpdate({...value, proactiveIntervalMinutes})}
                />
            </Flex>
        </Flex>
    );
}
