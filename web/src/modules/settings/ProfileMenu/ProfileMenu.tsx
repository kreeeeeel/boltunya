import {ArrowRightFromSquare, Sliders, Xmark} from '@gravity-ui/icons';
import {Button, DropdownMenu, Icon} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import type {Telegram} from '@/shared/api/client';
import type {SettingsSectionId} from '@/modules/settings/model/settingsSections';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import styles from '@/modules/settings/ProfileMenu/ProfileMenu.module.css';

export function ProfileMenu({
    telegram,
    onSettings,
    onLogout,
    onQuit,
}: {
    readonly telegram: Telegram;
    readonly onSettings: (section: SettingsSectionId) => void;
    readonly onLogout: () => void;
    readonly onQuit: () => void;
}) {
    const t = useTranslations();
    return (
        <DropdownMenu
            size="l"
            popupProps={{placement: 'right-end', className: styles.popup}}
            renderSwitcher={(props) => (
                <Button
                    {...props}
                    className={styles.button}
                    view="flat"
                    size="l"
                    aria-label={t('profileMenu')}
                >
                    <TelegramAvatar
                        title={telegram.name || t('settingsCenter.me')}
                        url={telegram.avatarUrl}
                        size="m"
                    />
                </Button>
            )}
            items={[
                [
                    {
                        text: t('settings'),
                        iconStart: <Icon data={Sliders} size={18} />,
                        action: () => onSettings('application'),
                    },
                ],
                [
                    {
                        text: t('logout'),
                        iconStart: <Icon data={ArrowRightFromSquare} size={18} />,
                        action: onLogout,
                    },
                    {
                        text: t('quit'),
                        iconStart: <Icon data={Xmark} size={18} />,
                        theme: 'danger',
                        action: onQuit,
                    },
                ],
            ]}
        />
    );
}
