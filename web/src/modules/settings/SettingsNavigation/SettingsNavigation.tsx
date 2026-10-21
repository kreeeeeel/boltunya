import {Flex, Menu, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useRef} from 'react';
import type {KeyboardEvent} from 'react';

import {settingsGroups} from '@/modules/settings/model/settingsSections';
import type {SettingsSectionId} from '@/modules/settings/model/settingsSections';
import styles from '@/modules/settings/SettingsNavigation/SettingsNavigation.module.css';

export function SettingsNavigation({
    selected,
    onSelect,
}: {
    readonly selected: SettingsSectionId;
    readonly onSelect: (section: SettingsSectionId) => void;
}) {
    const t = useTranslations('settingsCenter');
    const root = useRef<HTMLDivElement>(null);

    function navigate(event: Pick<KeyboardEvent, 'key' | 'preventDefault'>) {
        if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
            return;
        }
        const items = root.current?.querySelectorAll<HTMLElement>('[role="menuitem"]');
        if (!items?.length) {
            return;
        }
        const index = Array.from(items).findIndex((item) => item === document.activeElement);
        const next =
            event.key === 'Home'
                ? 0
                : event.key === 'End'
                  ? items.length - 1
                  : (index + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length;
        event.preventDefault();
        items[next].focus();
    }

    return (
        <Flex ref={root} direction="column" className={styles.root} onKeyDown={navigate}>
            <Text className={styles.brand}>Aigram</Text>
            <Menu size="l" aria-label={t('navigation')}>
                {settingsGroups.map((group) => (
                    <Menu.Group key={group.id} label={t('groups.' + group.id)}>
                        {group.sections.map((section) => (
                            <Menu.Item
                                key={section}
                                selected={selected === section}
                                onClick={() => onSelect(section)}
                                extraProps={{
                                    'aria-current': selected === section ? 'page' : undefined,
                                }}
                            >
                                {t('sections.' + section)}
                            </Menu.Item>
                        ))}
                    </Menu.Group>
                ))}
            </Menu>
        </Flex>
    );
}
