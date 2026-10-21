import {Box, Button, Flex, Icon, Loader, Text, Tooltip} from '@gravity-ui/uikit';
import {ArrowLeft, BookOpen, CircleInfo, Sliders} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import {Fragment, useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react';
import {messageDay} from '@/modules/workspace/model/messagePresentation';

import type {ConversationProps} from '@/modules/workspace/Conversation/Conversation.types';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
import {errorMessage} from '@/shared/api/client';
import {ConversationSkeleton} from '@/modules/workspace/ConversationSkeleton/ConversationSkeleton';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {ChatHeading} from '@/modules/workspace/ChatHeading/ChatHeading';
import {ConversationGeneration} from '@/modules/workspace/ConversationGeneration/ConversationGeneration';
import {MessageBubble} from '@/modules/workspace/Conversation/MessageBubble';
import {ConversationComposer} from '@/modules/workspace/Conversation/ConversationComposer';
import {MediaViewerProvider} from '@/modules/media/MediaViewer/MediaViewerProvider';

export function Conversation({
    chat,
    modelLabel,
    onMediaSent,
    onContextChanged,
    messages,
    activeReply,
    onRefresh,
    error,
    loading = false,
    onConfigure,
    onInspector,
    onMemory,
    onSend,
    onDraft,
    onBack,
    onEarlier,
}: ConversationProps) {
    const t = useTranslations();
    const locale = useLocale();
    const [today, setToday] = useState(() => messageDay(Date.now() / 1000));
    useEffect(() => {
        const timer = setInterval(() => setToday(messageDay(Date.now() / 1000)), 60000);
        return () => clearInterval(timer);
    }, []);
    const [text, setText] = useState('');
    const [busy, setBusy] = useState<'send' | 'draft' | ''>('');
    const [localError, setLocalError] = useState('');
    const [hasEarlier, setHasEarlier] = useState(true);
    const [loadingEarlier, setLoadingEarlier] = useState(false);
    const scroll = useRef<HTMLDivElement>(null);
    const stick = useRef(true);
    const lastChat = useRef(chat?.id);
    const pagination = useRef(false);
    const contextMenus = useRef(new Set<string>());
    const menuOpenChanged = useCallback((messageId: string, open: boolean) => {
        if (open) {
            contextMenus.current.add(messageId);
        } else {
            contextMenus.current.delete(messageId);
        }
    }, []);
    const anchor = useRef<{id: string; top: number; first: string} | null>(null);

    useLayoutEffect(() => {
        const area = scroll.current;
        const previous = anchor.current;
        if (area && previous && messages[0]?.id !== previous.first) {
            const element = area.querySelector<HTMLElement>(`[data-message-id="${CSS.escape(previous.id)}"]`);
            if (element) {
                area.scrollTop +=
                    element.getBoundingClientRect().top - area.getBoundingClientRect().top - previous.top;
            }
            anchor.current = null;
        }
    }, [messages]);

    async function earlier() {
        const area = scroll.current;
        if (!area || pagination.current || contextMenus.current.size || !hasEarlier || !messages.length) {
            return;
        }
        pagination.current = true;
        stick.current = false;
        const viewportTop = area.getBoundingClientRect().top;
        const visible = [...area.querySelectorAll<HTMLElement>('[data-message-id]')].find(
            (element) => element.getBoundingClientRect().bottom > viewportTop,
        );
        if (visible) {
            anchor.current = {
                id: visible.dataset.messageId ?? '',
                top: visible.getBoundingClientRect().top - viewportTop,
                first: messages[0].id,
            };
        }
        setLoadingEarlier(true);
        try {
            const more = await onEarlier();
            setHasEarlier(more);
            if (!more) {
                anchor.current = null;
            }
        } catch (cause) {
            anchor.current = null;
            setLocalError(errorMessage(cause));
        } finally {
            pagination.current = false;
            setLoadingEarlier(false);
        }
    }

    useEffect(() => {
        if (lastChat.current !== chat?.id) {
            lastChat.current = chat?.id;
            stick.current = true;
            contextMenus.current.clear();
        }
        if (stick.current && !contextMenus.current.size) {
            const area = scroll.current;
            if (area) {
                area.scrollTop = area.scrollHeight;
            }
        }
    }, [chat?.id, messages, activeReply?.id, activeReply?.thinking, activeReply?.phase]);

    async function send() {
        if (!text.trim() || busy) {
            return;
        }
        setBusy('send');
        setLocalError('');
        try {
            await onSend(text.trim());
            setText('');
            stick.current = true;
        } catch (cause) {
            setLocalError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }

    async function draft(messageId?: string) {
        if (busy) {
            return;
        }
        setBusy('draft');
        setLocalError('');
        try {
            setText(await onDraft(messageId));
        } catch (cause) {
            if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
                setLocalError(errorMessage(cause));
            }
        } finally {
            setBusy('');
        }
    }

    if (loading) {
        return <ConversationSkeleton />;
    }

    if (!chat) {
        return (
            <Flex
                className={styles.empty}
                direction="column"
                alignItems="center"
                justifyContent="center"
                gap={4}
            >
                <Box
                    as="img"
                    src="/assets/illustrations/conversations.png"
                    alt=""
                    className={styles.emptyIllustration}
                />
                <Text variant="header-2">{t('selectChat')}</Text>
                <Text color="secondary">{t('selectChatText')}</Text>
            </Flex>
        );
    }

    return (
        <MediaViewerProvider messages={messages}>
            <Flex direction="column" className={styles.root}>
                <Flex
                    qa="conversation-header"
                    alignItems="center"
                    justifyContent="space-between"
                    className={styles.header}
                >
                    <Flex alignItems="center" className={styles.identity}>
                        <Button className={styles.back} view="flat" aria-label={t('back')} onClick={onBack}>
                            <Icon data={ArrowLeft} />
                        </Button>
                        <ChatHeading chat={chat} />
                    </Flex>
                    <Flex className={styles.actions} alignItems="center">
                        <Tooltip content={t('autoNote')}>
                            <Text className={styles.model}>
                                {modelLabel ??
                                    chat.rule.model
                                        .replace('qwen3:', 'Qwen 3 · ')
                                        .replace('qwen2.5:', 'Qwen 2.5 · ')
                                        .replace('gemma3:', 'Gemma 3 · ')}
                            </Text>
                        </Tooltip>
                        <Tooltip content={t('memory.openChat')}>
                            <Button
                                className={styles.headerButton}
                                view="flat-secondary"
                                aria-label={t('memory.openChat')}
                                onClick={onMemory}
                            >
                                <Icon data={BookOpen} size={19} />
                            </Button>
                        </Tooltip>
                        <Tooltip content={t('configure')}>
                            <Button
                                className={styles.headerButton}
                                view="flat-secondary"
                                aria-label={t('configure')}
                                onClick={onConfigure}
                            >
                                <Icon data={Sliders} size={19} />
                            </Button>
                        </Tooltip>
                        <Button
                            className={[styles.inspectorButton, styles.headerButton].join(' ')}
                            view="flat-secondary"
                            aria-label={t('inspector')}
                            onClick={onInspector}
                        >
                            <Icon data={CircleInfo} size={20} />
                        </Button>
                    </Flex>
                </Flex>
                <Flex direction="column" className={styles.body} qa="conversation-body">
                    <ScrollArea
                        viewportRef={scroll}
                        qa="conversation-messages"
                        className={styles.messages}
                        onScroll={() => {
                            const el = scroll.current;
                            if (el && !contextMenus.current.size) {
                                stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 100;
                                if (el.scrollTop < 100) {
                                    void earlier();
                                }
                            }
                        }}
                    >
                        <Flex direction="column" className={styles.messageInner}>
                            {loadingEarlier && (
                                <Flex
                                    role="status"
                                    aria-label={t('loadingConversation')}
                                    className={styles.historyLoader}
                                >
                                    <Loader size="s" />
                                </Flex>
                            )}
                            {messages.map((message, index) => (
                                <Fragment key={message.id}>
                                    {(index === 0 ||
                                        messageDay(message.date) !==
                                            messageDay(messages[index - 1].date)) && (
                                        <Text className={styles.date} qa="message-date">
                                            {messageDay(message.date) === today
                                                ? t('today')
                                                : new Date(message.date * 1000).toLocaleDateString(locale, {
                                                      day: 'numeric',
                                                      month: 'long',
                                                  })}
                                        </Text>
                                    )}
                                    <MessageBubble
                                        message={message}
                                        onContextChanged={onContextChanged}
                                        onMenuOpenChange={menuOpenChanged}
                                        onDraftRequest={
                                            !message.outgoing ? () => draft(message.id) : undefined
                                        }
                                        nextMessage={messages[index + 1]}
                                        previousMessage={messages[index - 1]}
                                        group={
                                            chat.kind === 'group' ||
                                            chat.kind === 'supergroup' ||
                                            message.group
                                        }
                                        replyText={
                                            messages.find((item) => item.id === message.replyToId)?.text
                                        }
                                    />
                                </Fragment>
                            ))}
                            {messages.length === 0 && !error && (
                                <Text className={styles.noHistory}>{t('noHistory')}</Text>
                            )}
                            {activeReply && (
                                <ConversationGeneration
                                    key={activeReply.id}
                                    entry={activeReply}
                                    onRefresh={onRefresh}
                                    onRetry={() => draft()}
                                />
                            )}
                        </Flex>
                    </ScrollArea>
                    {(error || localError) && (
                        <Text color="danger" role="alert">
                            {localError || error}
                        </Text>
                    )}
                    <ConversationComposer
                        chatId={chat.id}
                        text={text}
                        onTextChange={setText}
                        busy={busy}
                        onSend={send}
                        onDraft={() => draft()}
                        onMediaSent={onMediaSent ?? onRefresh}
                    />
                </Flex>
            </Flex>
        </MediaViewerProvider>
    );
}
