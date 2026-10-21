import {
    ArrowRightFromSquare,
    BookOpen,
    Books,
    Globe,
    Palette,
    Sliders,
    Sparkles,
    Xmark,
} from '@gravity-ui/icons';
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
            popupProps={{placement: 'right-end'}}
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
                    {
                        text: t('globalPrompt'),
                        iconStart: <Icon data={Sliders} size={18} />,
                        action: () => onSettings('prompt'),
                    },
                    {
                        text: t('customStyles'),
                        iconStart: <Icon data={Sparkles} size={18} />,
                        action: () => onSettings('styles'),
                    },
                    {
                        text: t('memory.title'),
                        iconStart: <Icon data={BookOpen} size={18} />,
                        action: () => onSettings('memory'),
                    },
                    {
                        text: t('context.globalTitle'),
                        iconStart: <Icon data={Books} size={18} />,
                        action: () => onSettings('collection'),
                    },
                ],
                [
                    {
                        text: t('settingsCenter.sections.providers'),
                        iconStart: <Icon data={Sparkles} size={18} />,
                        action: () => onSettings('providers'),
                    },
                    {
                        text: t('settingsCenter.sections.appearance'),
                        iconStart: <Icon data={Palette} size={18} />,
                        action: () => onSettings('appearance'),
                    },
                    {
                        text: t('language'),
                        iconStart: <Icon data={Globe} size={18} />,
                        action: () => onSettings('language'),
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
