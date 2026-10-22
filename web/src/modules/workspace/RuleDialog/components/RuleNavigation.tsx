import {Flex, Icon, Menu, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useRef} from 'react';
import type {KeyboardEvent} from 'react';
import type {Chat} from '@/shared/api/client';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {ruleSectionGroups, ruleSectionIcons} from '@/modules/workspace/RuleDialog/model/ruleSections';
import type {RuleSectionId} from '@/modules/workspace/RuleDialog/model/ruleSections';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';

export function RuleNavigation({
    chat,
    selected,
    onSelect,
}: {
    readonly chat: Chat;
    readonly selected: RuleSectionId;
    readonly onSelect: (section: RuleSectionId) => void;
}) {
    const t = useTranslations('chatSettings');
    const root = useRef<HTMLDivElement>(null);

    function navigate(event: Pick<KeyboardEvent, 'key' | 'preventDefault'>) {
        if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
            return;
        }
        const items = Array.from(root.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
        if (!items.length) {
            return;
        }
        const current = items.findIndex((item) => item === document.activeElement);
        const next =
            event.key === 'Home'
                ? 0
                : event.key === 'End'
                  ? items.length - 1
                  : (current + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length;
        event.preventDefault();
        items[next].focus();
    }

    return (
        <Flex ref={root} direction="column" className={styles.navigation} onKeyDown={navigate}>
            <Flex alignItems="center" gap={3} className={styles.identity} qa="chat-settings-identity">
                <TelegramAvatar title={chat.title} url={chat.avatarUrl} />
                <Flex direction="column" gap={1} className={styles.identityText}>
                    <Text className={styles.chatName} title={chat.title}>
                        {chat.title}
                    </Text>
                    <Text color="secondary">{t('title')}</Text>
                </Flex>
            </Flex>
            <ScrollArea className={styles.navScroll} qa="chat-settings-navigation-scroll">
                <Menu size="l" aria-label={t('navigation')}>
                    {ruleSectionGroups.map((group) => (
                        <Menu.Group key={group.id} label={t('groups.' + group.id)}>
                            {group.sections
                                .filter(
                                    (section) =>
                                        section !== 'groups' || (chat.kind && chat.kind !== 'private'),
                                )
                                .map((section) => (
                                    <Menu.Item
                                        key={section}
                                        selected={selected === section}
                                        iconStart={<Icon data={ruleSectionIcons[section]} size={18} />}
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
