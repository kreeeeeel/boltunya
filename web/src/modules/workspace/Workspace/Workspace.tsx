'use client';

import {Box, Button, Flex, Icon, Text, Tooltip} from '@gravity-ui/uikit';
import {Clock, Comments, Moon, Sparkles, ChartColumn, CircleExclamation} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useEffect, useRef, useState} from 'react';

import {ActivityPanel} from '@/modules/activity/ActivityPanel/ActivityPanel';
import {ChatList} from '@/modules/workspace/ChatList/ChatList';
import {SettingsDialog} from '@/modules/workspace/SettingsDialog/SettingsDialog';
import {visibleChatActivity} from '@/modules/workspace/model/visibleChatActivity';
import {WindowBar} from '@/modules/workspace/WindowBar/WindowBar';
import {Conversation} from '@/modules/workspace/Conversation/Conversation';
import {ChatWallpaper} from '@/modules/workspace/ChatWallpaper/ChatWallpaper';
import {Inspector} from '@/modules/workspace/Inspector/Inspector';
import {ModelsPanel} from '@/modules/workspace/ModelsPanel/ModelsPanel';
import {MetricsPanel} from '@/modules/monitoring/MetricsPanel/MetricsPanel';
import {RuleDialog} from '@/modules/workspace/RuleDialog/RuleDialog';
import {useWorkspace} from '@/modules/workspace/hooks/useWorkspace';
import {Welcome} from '@/modules/workspace/Welcome/Welcome';
import {mergeMessages, mergeRecentMessages} from '@/modules/workspace/model/mergeMessages';
import styles from '@/modules/workspace/Workspace/Workspace.module.css';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Chat, ChatRule} from '@/shared/api/client';
import {usePreferences} from '@/shared/theme/Preferences';
import {desktopBridge} from '@/shared/desktop/bridge';
import {MemoryDialog} from '@/modules/memory/MemoryDialog/MemoryDialog';
import {ContextCollectionDialog} from '@/modules/memory/ContextCollectionDialog/ContextCollectionDialog';
import {DeletedMessagesDialog} from '@/modules/workspace/DeletedMessagesDialog/DeletedMessagesDialog';
import {ErrorsPanel} from '@/modules/diagnostics/ErrorsPanel/ErrorsPanel';

import {ProfileMenu} from '@/modules/settings/ProfileMenu/ProfileMenu';
import type {SettingsSectionId} from '@/modules/settings/model/settingsSections';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {useQueuedDraft} from '@/modules/workspace/hooks/useQueuedDraft';
import {applyContextExclusion} from '@/modules/messages/model/applyContextExclusion';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
import type {MessageDeletionResult} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';

export function Workspace() {
    const t = useTranslations();
    const modelProfiles = useModelProfiles();
    const {theme} = usePreferences();
    const [tab, setTab] = useState('chats');
    const [selected, setSelected] = useState<string | null>(null);
    const selectedChat = useRef<string | null>(null);
    const [settingsSection, setSettingsSection] = useState<SettingsSectionId | null>(null);
    const [memoryChat, setMemoryChat] = useState<Chat | null | undefined>();
    const [contextChat, setContextChat] = useState<Chat | null | undefined>();
    const [deletedChat, setDeletedChat] = useState<Chat | null>(null);
    useEffect(() => {
        const open = () => setSettingsSection('application');
        window.addEventListener('boltun-settings', open);
        window.addEventListener('aigram-settings', open);
        return () => {
            window.removeEventListener('boltun-settings', open);
            window.removeEventListener('aigram-settings', open);
        };
    }, []);
    const [editing, setEditing] = useState<Chat | null>(null);
    const [inspector, setInspector] = useState(false);
    const [actionError, setActionError] = useState('');
    const [quit, setQuit] = useState(false);
    const {state, messages, error, historyError, stateLoading, historyLoading, refresh, setMessages} =
        useWorkspace(selected);
    const chats = state?.chats ?? [];
    const chat = chats.find((item) => item.id === selected);
    const profileId =
        (chat?.rule.useDefaultModel ?? true)
            ? state?.settings.defaultModelProfileId
            : chat?.rule.modelProfileId;
    const modelLabel = modelProfiles.profiles.find((profile) => profile.id === profileId)?.name;
    const paused = state?.settings.paused ?? true;
    const queuedDraft = useQueuedDraft(chat?.id, refresh);
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
            const focused = document.activeElement;
            if (focused instanceof HTMLElement && focused.matches('[data-qa="chat-open"]')) {
                focused.blur();
            }
            event.preventDefault();
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
        await refreshConversation();
    }

    async function refreshConversation() {
        if (!chat) {
            return;
        }
        const latest = dataOrThrow(
            await api.GET('/api/chats/{chatId}/messages', {params: {path: {chatId: chat.id}}}),
        );
        setMessages((previous) =>
            selectedChat.current === chat.id ? mergeRecentMessages(previous, latest) : previous,
        );
        await refresh();
    }

    async function updateContextFlags(change: ContextExclusionChange) {
        if (chat && selectedChat.current === chat.id) {
            setMessages((previous) => applyContextExclusion(previous, chat.id, change));
        }
        await refresh();
    }

    async function messageDeleted(result: MessageDeletionResult) {
        setMessages((previous) =>
            previous.map((message) =>
                message.chatId === result.chatId && message.id === result.messageId
                    ? {...message, deleted: true, deletedAt: result.deletedAt}
                    : message,
            ),
        );
        await refresh();
    }

    async function draft(messageId?: string) {
        if (!chat) {
            return '';
        }
        return queuedDraft(chat.rule, messageId);
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
            <Flex direction="column" className={styles.root} qa="workspace-shell" data-aigram-css-scope="">
                <WindowBar state={state} onPause={pause} onError={setActionError} />
                <Welcome
                    state={state}
                    loading={stateLoading}
                    error={actionError || error}
                    onRefresh={refresh}
                />
                {settingsSection !== null && state && (
                    <SettingsDialog
                        settings={state.settings}
                        engine={state.engine}
                        initialSection={settingsSection}
                        onClose={() => setSettingsSection(null)}
                        onRefresh={refresh}
                    />
                )}
            </Flex>
        );
    }

    return (
        <Flex direction="column" className={styles.root} qa="workspace-shell" data-aigram-css-scope="">
            {tab === 'chats' && chat && (
                <ChatWallpaper
                    wallpaper={theme === 'dark' ? (chat.darkWallpaper ?? chat.wallpaper) : chat.wallpaper}
                />
            )}
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
                            {id: 'errors', icon: CircleExclamation},
                        ].map((item) => (
                            <Tooltip
                                key={item.id}
                                content={t(
                                    item.id === 'errors'
                                        ? 'diagnostics.title'
                                        : item.id === 'metrics'
                                          ? item.id + 'Nav'
                                          : item.id,
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
                                        item.id === 'errors'
                                            ? 'diagnostics.title'
                                            : item.id === 'metrics'
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
                                onClick={() => setSettingsSection('appearance')}
                            >
                                <Icon data={Moon} size={21} />
                            </Button>
                        </Tooltip>
                        <ProfileMenu
                            telegram={state.telegram}
                            onSettings={setSettingsSection}
                            onLogout={() => void logout()}
                            onQuit={() => void stop()}
                        />
                    </Flex>
                </Flex>
                <Flex key={tab} className={styles.view} qa="workspace-view">
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
                                    modelLabel={modelLabel}
                                    onMediaSent={refreshConversation}
                                    onContextChanged={updateContextFlags}
                                    onMessageDeleted={messageDeleted}
                                    messages={messages}
                                    activeReply={visibleChatActivity(state.activity, selected)}
                                    onRefresh={refresh}
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
                                        modelLabel={modelLabel}
                                        customStyles={state.settings.customStyles ?? []}
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
                    {tab === 'errors' && <ErrorsPanel />}
                    {tab === 'models' && !stateLoading && (
                        <Box className={styles.panel}>
                            <ModelsPanel
                                engine={state?.engine}
                                defaultModel={state?.settings.defaultModel ?? 'gemma3:4b'}
                                defaultModelProfileId={state?.settings.defaultModelProfileId}
                                onRefresh={refresh}
                            />
                        </Box>
                    )}
                    {tab === 'activity' && !stateLoading && (
                        <ActivityPanel
                            entries={state?.activity ?? []}
                            queue={state?.replyQueue}
                            assistantPaused={paused}
                            maxParallelTasks={state?.settings.maxParallelTasks ?? 1}
                            onRefresh={refresh}
                        />
                    )}
                </Flex>
            </Flex>

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

            {settingsSection !== null && (
                <SettingsDialog
                    settings={state.settings}
                    engine={state.engine}
                    initialSection={settingsSection}
                    onClose={() => setSettingsSection(null)}
                    onRefresh={refresh}
                />
            )}
            {editing && (
                <RuleDialog
                    key={`rule:${editing.id}`}
                    chat={editing}
                    engine={state?.engine}
                    defaultModel={state.settings.defaultModel}
                    defaultModelProfileId={state.settings.defaultModelProfileId}
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
