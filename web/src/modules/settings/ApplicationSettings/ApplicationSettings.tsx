import {Box, Flex, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';

export function ApplicationSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <Switch checked={value.closeToTray} onUpdate={(closeToTray) => onUpdate({...value, closeToTray})}>
                {t('closeToTray')}
            </Switch>
            <Switch
                checked={value.launchAtLogin}
                onUpdate={(launchAtLogin) => onUpdate({...value, launchAtLogin})}
            >
                {t('launchAtLogin')}
            </Switch>
            <Box>
                <Switch
                    checked={value.resumeOnStartup}
                    onUpdate={(resumeOnStartup) => onUpdate({...value, resumeOnStartup})}
                >
                    {t('resumeOnStartup')}
                </Switch>
                <Text as="p" color="secondary">
                    {t('resumeOnStartupHint')}
                </Text>
            </Box>
        </Flex>
    );
}
