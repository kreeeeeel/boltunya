import {Box, Button, Flex, Icon, Loader, Skeleton, Text, TextArea, Tooltip} from '@gravity-ui/uikit';
import {ArrowLeft, BookOpen, CircleInfo, PaperPlane, Sliders, Sparkles} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import {Fragment, useEffect, useLayoutEffect, useRef, useState} from 'react';
import {messageDay} from '@/modules/workspace/model/messagePresentation';

import type {ConversationProps} from '@/modules/workspace/Conversation/Conversation.types';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
import {errorMessage} from '@/shared/api/client';
import {ConversationSkeleton} from '@/modules/workspace/ConversationSkeleton/ConversationSkeleton';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {ChatHeading} from '@/modules/workspace/ChatHeading/ChatHeading';
import {ReplySkeleton} from '@/shared/ui/ReplySkeleton/ReplySkeleton';
import {MessageBubble} from '@/modules/workspace/Conversation/MessageBubble';

export function Conversation({
    chat,
    messages,
    generating,
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
        if (!area || pagination.current || !hasEarlier || !messages.length) {
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
        }
        if (stick.current) {
            const area = scroll.current;
            if (area) {
                area.scrollTop = area.scrollHeight;
            }
        }
    }, [chat?.id, messages]);

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

    async function draft() {
        setBusy('draft');
        setLocalError('');
        try {
            setText(await onDraft());
        } catch (cause) {
            setLocalError(errorMessage(cause));
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
                            {chat.rule.model
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
                        if (el) {
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
                                    messageDay(message.date) !== messageDay(messages[index - 1].date)) && (
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
                                    nextMessage={messages[index + 1]}
                                    replyText={messages.find((item) => item.id === message.replyToId)?.text}
                                />
                            </Fragment>
                        ))}
                        {messages.length === 0 && !error && (
                            <Text className={styles.noHistory}>{t('noHistory')}</Text>
                        )}
                        {generating && (
                            <Box className={[styles.bubble, styles.outgoing].join(' ')}>
                                <ReplySkeleton />
                            </Box>
                        )}
                    </Flex>
                </ScrollArea>
                <Box className={styles.composerArea}>
                    {(error || localError) && <Text color="danger">{localError || error}</Text>}
                    <Flex alignItems="flex-end" gap={2} className={styles.composer} qa="message-composer">
                        <Tooltip content={t('draft')}>
                            <Button
                                size="m"
                                className={styles.composerButton}
                                view="flat-secondary"
                                loading={busy === 'draft'}
                                disabled={!!busy}
                                onClick={() => void draft()}
                                aria-label={t('draft')}
                            >
                                <Icon data={Sparkles} size={19} />
                            </Button>
                        </Tooltip>
                        {busy === 'draft' ? (
                            <Skeleton className={styles.draftSkeleton} height={18} width="65%" />
                        ) : (
                            <TextArea
                                view="clear"
                                size="l"
                                className={styles.input}
                                value={text}
                                disabled={busy === 'send'}
                                onUpdate={setText}
                                minRows={1}
                                maxRows={5}
                                placeholder={t('messagePlaceholder')}
                                controlProps={{'aria-label': t('messagePlaceholder'), maxLength: 4096}}
                                onKeyDown={(event) => {
                                    if (event.key === 'Enter' && !event.shiftKey) {
                                        event.preventDefault();
                                        void send();
                                    }
                                }}
                            />
                        )}
                        <Button
                            view="action"
                            size="m"
                            loading={busy === 'send'}
                            disabled={!text.trim() || !!busy}
                            onClick={() => void send()}
                            aria-label={t('send')}
                            className={styles.composerButton}
                        >
                            <Icon data={PaperPlane} size={18} />
                        </Button>
                    </Flex>
                </Box>
            </Flex>
        </Flex>
    );
}
