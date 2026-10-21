import {Flex, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {ReactNode} from 'react';

import type {SettingsSectionId} from '@/modules/settings/model/settingsSections';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import styles from '@/modules/settings/SettingsSection/SettingsSection.module.css';

export function SettingsSection({
    id,
    children,
    actions,
}: {
    readonly id: SettingsSectionId;
    readonly children: ReactNode;
    readonly actions?: ReactNode;
}) {
    const t = useTranslations('settingsCenter');
    return (
        <Flex direction="column" className={styles.root}>
            <ScrollArea className={styles.scroll} qa={'settings-content-' + id}>
                <Flex direction="column" className={styles.content} gap={5}>
                    <Flex direction="column" gap={2} className={styles.heading}>
                        <Text variant="header-2">{t('sections.' + id)}</Text>
                        <Text color="secondary" className={styles.description}>
                            {t('descriptions.' + id)}
                        </Text>
                    </Flex>
                    {children}
                </Flex>
            </ScrollArea>
            {actions && (
                <Flex className={styles.actions} justifyContent="flex-end" alignItems="center" gap={3}>
                    {actions}
                </Flex>
            )}
        </Flex>
    );
}
