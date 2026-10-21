import {
    BookOpen,
    Books,
    Comments,
    Cpu,
    Folder,
    Gear,
    Globe,
    Palette,
    Pencil,
    Picture,
    Route,
    Sliders,
    Sparkles,
    TrashBin,
} from '@gravity-ui/icons';
import {Box, Flex, Icon, Menu, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useRef} from 'react';
import type {KeyboardEvent} from 'react';

import {settingsGroups} from '@/modules/settings/model/settingsSections';
import type {SettingsSectionId} from '@/modules/settings/model/settingsSections';
import styles from '@/modules/settings/SettingsNavigation/SettingsNavigation.module.css';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';

const sectionIcons = {
    application: Gear,
    replies: Comments,
    prompt: Pencil,
    styles: Sparkles,
    providers: Sliders,
    routing: Route,
    compute: Cpu,
    search: Globe,
    memory: BookOpen,
    collection: Books,
    appearance: Palette,
    language: Globe,
    media: Picture,
    cache: TrashBin,
    storage: Folder,
} satisfies Record<SettingsSectionId, typeof Gear>;

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
            <Flex className={styles.brand} alignItems="center" gap={3} qa="settings-brand">
                <Box as="img" src="/assets/brand/aigram.svg" width={28} height={28} alt="" />
                <Text>Aigram</Text>
            </Flex>
            <ScrollArea className={styles.scroll} qa="settings-navigation-scroll">
                <Menu size="l" aria-label={t('navigation')}>
                    {settingsGroups.map((group) => (
                        <Menu.Group key={group.id} label={t('groups.' + group.id)}>
                            {group.sections.map((section) => (
                                <Menu.Item
                                    key={section}
                                    selected={selected === section}
                                    title={t('sections.' + section)}
                                    iconStart={<Icon data={sectionIcons[section]} size={18} />}
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
            </ScrollArea>
        </Flex>
    );
}
