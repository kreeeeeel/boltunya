import {Button, Flex, Icon, Label, Loader, Text, Tooltip} from '@gravity-ui/uikit';
import {Sliders} from '@gravity-ui/icons';
import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {AppState, Chat} from '@/shared/api/client';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import {configuredProactiveChats} from '@/modules/settings/ProactiveChats/configuredProactiveChats';
import styles from '@/modules/settings/ProactiveChats/ProactiveChats.module.css';

const PAGE_SIZE = 30;

export function ProactiveChats({
    chats,
    settings,
    ownerId,
    disabled = false,
    onConfigure,
}: {
    readonly chats: Chat[];
    readonly settings: AppState['settings'];
    readonly ownerId?: string;
    readonly disabled?: boolean;
    readonly onConfigure?: (chat: Chat) => void;
}) {
    const t = useTranslations('proactive');
    const active = useMemo(
        () => configuredProactiveChats(chats, settings, ownerId),
        [chats, settings, ownerId],
    );
    const [limit, setLimit] = useState(PAGE_SIZE);
    const [loading, setLoading] = useState(false);
    const frame = useRef(0);
    useEffect(() => () => cancelAnimationFrame(frame.current), []);

    function more() {
        if (frame.current || loading) {
            return;
        }
        setLoading(true);
        frame.current = requestAnimationFrame(() => {
            frame.current = requestAnimationFrame(() => {
                frame.current = 0;
                setLimit((previous) => previous + PAGE_SIZE);
                setLoading(false);
            });
        });
    }

    return (
        <Flex direction="column" gap={3} qa="proactive-chats">
            <Flex alignItems="center" gap={2}>
                <Text variant="subheader-2">{t('activeChatsTitle')}</Text>
                <Text color="secondary">{active.length}</Text>
            </Flex>
            {disabled && active.length > 0 && <Text color="secondary">{t('saveBeforeChat')}</Text>}
            {active.length === 0 && <Text color="secondary">{t('emptyChats')}</Text>}
            {active.slice(0, limit).map((chat) => (
                <Flex key={chat.id} gap={3} alignItems="center" className={styles.row} qa="proactive-chat">
                    <TelegramAvatar title={chat.title} url={chat.avatarUrl} size="m" />
                    <Flex direction="column" gap={2} className={styles.identity}>
                        <Text className={styles.title} ellipsis>
                            {chat.title}
                        </Text>
                        <Flex gap={2} alignItems="center" wrap>
                            <Label theme={chat.rule.proactiveMessageMode === 'text' ? 'normal' : 'info'}>
                                {t('messageModes.' + (chat.rule.proactiveMessageMode ?? 'ai'))}
                            </Label>
                            <Text color="secondary">
                                {t('listInterval', {
                                    minutes:
                                        chat.rule.proactiveIntervalMinutes ||
                                        settings.proactiveIntervalMinutes ||
                                        60,
                                })}
                            </Text>
                        </Flex>
                    </Flex>
                    {onConfigure && (
                        <Tooltip content={t('configureChat', {name: chat.title})}>
                            <Button
                                size="m"
                                view="flat-secondary"
                                disabled={disabled}
                                onClick={() => onConfigure(chat)}
                                aria-label={t('configureChat', {name: chat.title})}
                            >
                                <Icon data={Sliders} size={18} />
                            </Button>
                        </Tooltip>
                    )}
                </Flex>
            ))}
            {limit < active.length && (
                <Flex justifyContent="center">
                    <Button view="flat-info" disabled={loading} onClick={more}>
                        {loading && <Loader size="s" />}
                        {t('loadMore')}
                    </Button>
                </Flex>
            )}
        </Flex>
    );
}
