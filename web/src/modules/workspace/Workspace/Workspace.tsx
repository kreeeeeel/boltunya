'use client';

import {Box, Button, DropdownMenu, Flex, Icon, Label, Text, Tooltip} from '@gravity-ui/uikit';
import {
    ArrowRightFromSquare,
    Clock,
    Comments,
    Moon,
    Sparkles,
    Globe,
    Sliders,
    Xmark,
    ChartColumn,
    NodesRight,
    BookOpen,
    Books,
} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useEffect, useRef, useState} from 'react';

import {ChatList} from '@/modules/workspace/ChatList/ChatList';
import {GlobalPromptDialog} from '@/modules/workspace/GlobalPromptDialog/GlobalPromptDialog';
import {StylesDialog} from '@/modules/workspace/StylesDialog/StylesDialog';
import {SettingsDialog} from '@/modules/workspace/SettingsDialog/SettingsDialog';
import {WindowBar} from '@/modules/workspace/WindowBar/WindowBar';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import {Conversation} from '@/modules/workspace/Conversation/Conversation';
import {ChatWallpaper} from '@/modules/workspace/ChatWallpaper/ChatWallpaper';
import {Inspector} from '@/modules/workspace/Inspector/Inspector';
import {ModelsPanel} from '@/modules/workspace/ModelsPanel/ModelsPanel';
import {MetricsPanel} from '@/modules/monitoring/MetricsPanel/MetricsPanel';
import {FlowEditor} from '@/modules/flows/FlowEditor/FlowEditor';
import {RuleDialog} from '@/modules/workspace/RuleDialog/RuleDialog';
import {useWorkspace} from '@/modules/workspace/hooks/useWorkspace';
import {Welcome} from '@/modules/workspace/Welcome/Welcome';
import {mergeMessages} from '@/modules/workspace/model/mergeMessages';
import styles from '@/modules/workspace/Workspace/Workspace.module.css';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Chat, ChatRule} from '@/shared/api/client';
import {usePreferences} from '@/shared/theme/Preferences';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {useLanguageItems} from '@/shared/ui/LanguageMenu/LanguageMenu';
import {desktopBridge} from '@/shared/desktop/bridge';
import {MemoryDialog} from '@/modules/memory/MemoryDialog/MemoryDialog';
import {ContextCollectionDialog} from '@/modules/memory/ContextCollectionDialog/ContextCollectionDialog';
import {DeletedMessagesDialog} from '@/modules/workspace/DeletedMessagesDialog/DeletedMessagesDialog';
import {PanelSkeleton} from '@/shared/ui/PanelSkeleton/PanelSkeleton';

export function Workspace() {
    const t = useTranslations();
    const {toggleTheme, theme} = usePreferences();
    const languageItems = useLanguageItems();
    const [tab, setTab] = useState('chats');
    const [selected, setSelected] = useState<string | null>(null);
    const selectedChat = useRef<string | null>(null);
    const [promptOpen, setPromptOpen] = useState(false);
    const [stylesOpen, setStylesOpen] = useState(false);
    const [settingsOpen, setSettingsOpen] = useState(false);
    const [memoryChat, setMemoryChat] = useState<Chat | null | undefined>();
    const [contextChat, setContextChat] = useState<Chat | null | undefined>();
    const [deletedChat, setDeletedChat] = useState<Chat | null>(null);
    useEffect(() => {
        const open = () => setSettingsOpen(true);
        window.addEventListener('boltun-settings', open);
        return () => window.removeEventListener('boltun-settings', open);
    }, []);
    const [editing, setEditing] = useState<Chat | null>(null);
    const [inspector, setInspector] = useState(false);
    const [actionError, setActionError] = useState('');
    const [quit, setQuit] = useState(false);
    const {state, messages, error, historyError, stateLoading, historyLoading, refresh, setMessages} =
        useWorkspace(selected);
    const chats = state?.chats ?? [];
    const chat = chats.find((item) => item.id === selected);
    const paused = state?.settings.paused ?? true;
    useEffect(() => {
        if (!selected || tab !== 'chats') {
            return;
        }
        const closeChat = (event: KeyboardEvent) => {
            if (
                event.key !== 'Escape' ||
                event.defaultPrevented ||
                document.querySelector('[role="dialog"], [role="menu"], [role="listbox"]')
            ) {
                return;
            }
            selectedChat.current = null;
            setSelected(null);
            setMessages([]);
            setInspector(false);
        };
        window.addEventListener('keydown', closeChat);
        return () => window.removeEventListener('keydown', closeChat);
    }, [selected, tab, setMessages]);
    async function saveRule(target: Chat, rule: ChatRule) {
        dataOrThrow(
            await api.PUT('/api/chats/{chatId}/rule', {params: {path: {chatId: target.id}}, body: rule}),
        );
        await refresh();
    }

    async function toggleRule(target: Chat, rule: ChatRule) {
        try {
            await saveRule(target, rule);
            setActionError('');
        } catch (cause) {
            setActionError(errorMessage(cause));
        }
    }

    async function pause() {
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: {paused: !paused}}));
            await refresh();
        } catch (cause) {
            setActionError(errorMessage(cause));
        }
    }

    async function send(text: string) {
        if (!chat) {
            return;
        }
        dataOrThrow(
            await api.POST('/api/chats/{chatId}/messages', {params: {path: {chatId: chat.id}}, body: {text}}),
        );
        const latest = dataOrThrow(
            await api.GET('/api/chats/{chatId}/messages', {params: {path: {chatId: chat.id}}}),
        );
        setMessages((previous) =>
            selectedChat.current === chat.id ? mergeMessages(previous, latest) : previous,
        );
        await refresh();
    }

    async function draft() {
        if (!chat) {
            return '';
        }
        const last = [...messages].reverse().find((message) => !message.outgoing);
        const result = dataOrThrow(
            await api.POST('/api/preview', {
                body: {
                    text: last?.text ?? t('testPlaceholder'),
                    rule: chat.rule,
                    chatId: chat.id,
                },
            }),
        );
        return result.text;
    }

    function select(id: string) {
        if (id === selected) {
            return;
        }
        selectedChat.current = id;
        setSelected(id);
        setMessages([]);
    }

    async function earlier() {
        if (!chat || messages.length === 0) {
            return false;
        }
        const page = dataOrThrow(
            await api.GET('/api/chats/{chatId}/messages', {
                params: {path: {chatId: chat.id}, query: {before: messages[0].id}},
            }),
        );
        if (selectedChat.current !== chat.id) {
            return false;
        }
        setMessages((previous) =>
            selectedChat.current === chat.id ? mergeMessages(previous, page) : previous,
        );
        return page.some((message) => !messages.some((existing) => existing.id === message.id));
    }

    async function logout() {
        try {
            dataOrThrow(await api.POST('/api/telegram/logout'));
            selectedChat.current = null;
            setSelected(null);
            setMessages([]);
            await refresh();
        } catch (cause) {
            setActionError(errorMessage(cause));
        }
    }

    async function stop() {
        try {
            const desktop = desktopBridge();
            if (desktop) {
                await desktop.quit();
            } else {
                dataOrThrow(await api.POST('/api/quit'));
            }
            setQuit(true);
        } catch (cause) {
            setActionError(errorMessage(cause));
        }
    }

    if (state?.telegram.state !== 'ready') {
        return (
            <Flex direction="column" className={styles.root} qa="workspace-shell">
                <ChatWallpaper />
                <WindowBar state={state} onPause={pause} onError={setActionError} />
                <Welcome
                    state={state}
                    loading={stateLoading}
                    error={actionError || error}
                    onRefresh={refresh}
                />
                {settingsOpen && state && (
                    <SettingsDialog
                        settings={state.settings}
                        onClose={() => setSettingsOpen(false)}
                        onRefresh={refresh}
                    />
                )}
            </Flex>
        );
    }

    return (
        <Flex direction="column" className={styles.root} qa="workspace-shell">
            <ChatWallpaper
                wallpaper={theme === 'dark' ? (chat?.darkWallpaper ?? chat?.wallpaper) : chat?.wallpaper}
            />
            <WindowBar state={state} onPause={pause} onError={setActionError} />
            {(error || actionError || quit) && (
                <Flex className={styles.error} gap={3} alignItems="center">
                    <Text>{quit ? t('quitDone') : actionError || error}</Text>
                    {!quit && (
                        <Button
                            size="s"
                            onClick={() => {
                                setActionError('');
                                void refresh();
                            }}
                        >
                            {t('retry')}
                        </Button>
                    )}
                </Flex>
            )}
            <Flex className={styles.main}>
                <Flex direction="column" alignItems="center" className={styles.rail} qa="sidebar">
                    <Flex direction="column" className={styles.nav} qa="sidebar-navigation">
                        {[
                            {id: 'chats', icon: Comments},
                            {id: 'models', icon: Sparkles},
                            {id: 'activity', icon: Clock},
                            {id: 'metrics', icon: ChartColumn},
                            {id: 'flows', icon: NodesRight},
                        ].map((item) => (
                            <Tooltip
                                key={item.id}
                                content={t(
                                    item.id === 'metrics' || item.id === 'flows' ? item.id + 'Nav' : item.id,
                                )}
                                placement="right"
                            >
                                <Button
                                    view={tab === item.id ? 'flat-info' : 'flat-secondary'}
                                    className={styles.navButton}
                                    size="l"
                                    qa={'nav-' + item.id}
                                    aria-current={tab === item.id ? 'page' : undefined}
                                    onClick={() => setTab(item.id)}
                                    aria-label={t(
                                        item.id === 'metrics' || item.id === 'flows'
                                            ? item.id + 'Nav'
                                            : item.id,
                                    )}
                                >
                                    <Icon data={item.icon} size={21} />
                                </Button>
                            </Tooltip>
                        ))}
                    </Flex>
                    <Flex direction="column" className={styles.navBottom}>
                        <Tooltip content={t('theme')} placement="right">
                            <Button
                                className={styles.navButton}
                                size="l"
                                view="flat-secondary"
                                aria-label={t('theme')}
                                onClick={toggleTheme}
                            >
                                <Icon data={Moon} size={21} />
                            </Button>
                        </Tooltip>
                        <DropdownMenu
                            size="l"
                            popupProps={{placement: 'right-end'}}
                            renderSwitcher={(props) => (
                                <Button
                                    {...props}
                                    className={styles.profileButton}
                                    view="flat"
                                    size="l"
                                    aria-label={t('profileMenu')}
                                >
                                    <TelegramAvatar
                                        title={state.telegram.name || 'Я'}
                                        url={state.telegram.avatarUrl}
                                        size="m"
                                    />
                                </Button>
                            )}
                            items={[
                                [
                                    {
                                        text: t('settings'),
                                        iconStart: <Icon data={Sliders} size={18} />,
                                        action: () => setSettingsOpen(true),
                                    },
                                    {
                                        text: t('customStyles'),
                                        iconStart: <Icon data={Sparkles} size={18} />,
                                        action: () => setStylesOpen(true),
                                    },
                                    {
                                        text: t('globalPrompt'),
                                        iconStart: <Icon data={Sliders} size={18} />,
                                        action: () => setPromptOpen(true),
                                    },
                                    {
                                        text: t('memory.title'),
                                        iconStart: <Icon data={BookOpen} size={18} />,
                                        action: () => setMemoryChat(null),
                                    },
                                    {
                                        text: t('context.globalTitle'),
                                        iconStart: <Icon data={Books} size={18} />,
                                        action: () => setContextChat(null),
                                    },
                                ],
                                [
                                    {
                                        text: t('language'),
                                        iconStart: <Icon data={Globe} size={18} />,
                                        items: languageItems,
                                    },
                                    {
                                        text: t('logout'),
                                        iconStart: <Icon data={ArrowRightFromSquare} size={18} />,
                                        action: () => void logout(),
                                    },
                                    {
                                        text: t('quit'),
                                        iconStart: <Icon data={Xmark} size={18} />,
                                        theme: 'danger',
                                        action: () => void stop(),
                                    },
                                ],
                            ]}
                        />
                    </Flex>
                </Flex>
                <Flex key={tab} className={styles.view} qa="workspace-view">
                    {stateLoading && tab !== 'chats' && (
                        <Box className={styles.panel}>
                            <PanelSkeleton />
                        </Box>
                    )}
                    {tab === 'chats' && (
                        <>
                            <Box className={`${styles.chatList} ${selected ? styles.hideOnMobile : ''}`}>
                                <ChatList
                                    chats={chats}
                                    selected={selected}
                                    loading={stateLoading}
                                    onSelect={select}
                                />
                            </Box>
                            <Box className={`${styles.conversation} ${!selected ? styles.hideOnMobile : ''}`}>
                                <Conversation
                                    key={chat?.id ?? 'empty'}
                                    chat={chat}
                                    messages={messages}
                                    generating={
                                        state?.activity.some(
                                            (item) =>
                                                item.chatId === selected && item.status === 'generating',
                                        ) ?? false
                                    }
                                    error={historyError}
                                    loading={stateLoading || historyLoading}
                                    onConfigure={() => {
                                        if (chat) {
                                            setEditing(chat);
                                        }
                                    }}
                                    onInspector={() => setInspector(!inspector)}
                                    onMemory={() => {
                                        if (chat) {
                                            setMemoryChat(chat);
                                        }
                                    }}
                                    onSend={send}
                                    onDraft={draft}
                                    onBack={() => {
                                        selectedChat.current = null;
                                        setSelected(null);
                                        setMessages([]);
                                        setInspector(false);
                                    }}
                                    onEarlier={earlier}
                                />
                            </Box>
                            {chat && inspector && (
                                <Box className={styles.inspector}>
                                    <Inspector
                                        chat={chat}
                                        onConfigure={() => setEditing(chat)}
                                        onCollectContext={() => setContextChat(chat)}
                                        onDeleted={() => setDeletedChat(chat)}
                                        onEnable={(enabled) => void toggleRule(chat, {...chat.rule, enabled})}
                                        onClose={() => setInspector(false)}
                                    />
                                </Box>
                            )}
                        </>
                    )}
                    {tab === 'metrics' && (
                        <Box className={styles.panel}>
                            <MetricsPanel />
                        </Box>
                    )}
                    {tab === 'flows' && (
                        <Box className={styles.panel}>
                            <FlowEditor chats={chats} />
                        </Box>
                    )}
                    {tab === 'models' && !stateLoading && (
                        <Box className={styles.panel}>
                            <ModelsPanel
                                engine={state?.engine}
                                defaultModel={state?.settings.defaultModel ?? 'gemma3:4b'}
                                onRefresh={refresh}
                            />
                        </Box>
                    )}
                    {tab === 'activity' && !stateLoading && (
                        <ScrollArea className={styles.infoPanel}>
                            <Box className={styles.activityContent}>
                                {!!state?.activity.length && (
                                    <>
                                        <Text variant="header-2">{t('activityTitle')}</Text>
                                        <Text className={styles.infoDescription}>
                                            {t('activityDescription')}
                                        </Text>
                                    </>
                                )}
                                {!state?.activity.length && (
                                    <Flex
                                        direction="column"
                                        gap={3}
                                        className={styles.emptyActivity}
                                        qa="empty-activity"
                                    >
                                        <Box
                                            as="img"
                                            src="/assets/illustrations/activity.png"
                                            alt=""
                                            className={styles.emptyIllustration}
                                        />
                                        <Text variant="header-1">{t('noActivity')}</Text>
                                        <Text color="secondary">{t('noActivityText')}</Text>
                                    </Flex>
                                )}
                                {state?.activity.map((item) => (
                                    <Flex
                                        key={item.id}
                                        gap={4}
                                        justifyContent="space-between"
                                        className={styles.activityRow}
                                    >
                                        <Flex direction="column" gap={2}>
                                            <Text variant="subheader-2">{item.title}</Text>
                                            <Text color="secondary">{item.detail}</Text>
                                        </Flex>
                                        <Label theme={item.status === 'error' ? 'danger' : 'info'}>
                                            {t(item.status)}
                                        </Label>
                                    </Flex>
                                ))}
                            </Box>
                        </ScrollArea>
                    )}
                </Flex>
            </Flex>
            {stylesOpen && (
                <StylesDialog
                    styles={state.settings.customStyles ?? []}
                    onClose={() => setStylesOpen(false)}
                    onRefresh={refresh}
                />
            )}
            {contextChat !== undefined && (
                <ContextCollectionDialog
                    key={`context:${contextChat?.id ?? 'global'}`}
                    chatId={contextChat?.id ?? ''}
                    title={contextChat?.title ?? t('memory.title')}
                    onClose={() => setContextChat(undefined)}
                />
            )}
            {deletedChat && (
                <DeletedMessagesDialog
                    key={`deleted:${deletedChat.id}`}
                    chatId={deletedChat.id}
                    title={deletedChat.title}
                    onClose={() => setDeletedChat(null)}
                />
            )}
            {memoryChat !== undefined && (
                <MemoryDialog
                    key={`memory:${memoryChat?.id ?? 'global'}`}
                    chatId={memoryChat?.id}
                    title={memoryChat?.title}
                    onClose={() => setMemoryChat(undefined)}
                />
            )}
            {promptOpen && (
                <GlobalPromptDialog
                    value={state.settings.globalPrompt ?? ''}
                    onClose={() => setPromptOpen(false)}
                    onRefresh={refresh}
                />
            )}
            {settingsOpen && (
                <SettingsDialog
                    settings={state.settings}
                    onClose={() => setSettingsOpen(false)}
                    onRefresh={refresh}
                />
            )}
            {editing && (
                <RuleDialog
                    key={`rule:${editing.id}`}
                    chat={editing}
                    engine={state?.engine}
                    customStyles={state.settings.customStyles ?? []}
                    onClose={() => setEditing(null)}
                    onCollectContext={() => setContextChat(editing)}
                    onDeleted={() => setDeletedChat(editing)}
                    onSave={(rule) => saveRule(editing, rule)}
                />
            )}
        </Flex>
    );
}
