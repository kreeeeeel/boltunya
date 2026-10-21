import {Flex, Select, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {SettingsDraftSectionProps} from '@/modules/settings/model/SettingsDraftSectionProps';

export function MediaSettings({value, onUpdate}: SettingsDraftSectionProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={4}>
            {(
                [
                    'autoDownloadPhotos',
                    'autoDownloadVideos',
                    'autoDownloadAudio',
                    'autoDownloadFiles',
                ] as const
            ).map((key) => (
                <Switch
                    key={key}
                    checked={value[key]}
                    onUpdate={(enabled) => onUpdate({...value, [key]: enabled})}
                >
                    {t('storage.' + key)}
                </Switch>
            ))}
            <Text>{t('storage.limit')}</Text>
            <Select
                size="l"
                width="max"
                value={[String(value.autoDownloadMaxMb)]}
                options={Array.from(new Set([5, 10, 20, 50, 100, value.autoDownloadMaxMb]))
                    .sort((a, b) => a - b)
                    .map((size) => ({
                        value: String(size),
                        content: t('storage.megabytes', {count: String(size)}),
                    }))}
                onUpdate={([size]) => onUpdate({...value, autoDownloadMaxMb: Number(size)})}
                aria-label={t('storage.limit')}
            />
            <Text color="secondary">{t('storage.autoHint')}</Text>
        </Flex>
    );
}
