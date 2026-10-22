import {Box, Button, Flex, Icon, Loader, Text, Tooltip} from '@gravity-ui/uikit';
import {ArrowLeft, CircleInfo, Sliders} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react';
import {messageDay} from '@/modules/workspace/model/messagePresentation';

import type {ConversationProps} from '@/modules/workspace/Conversation/Conversation.types';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
import {errorMessage} from '@/shared/api/client';
import {ConversationSkeleton} from '@/modules/workspace/ConversationSkeleton/ConversationSkeleton';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {ChatHeading} from '@/modules/workspace/ChatHeading/ChatHeading';
import {ConversationGeneration} from '@/modules/workspace/ConversationGeneration/ConversationGeneration';
import {ConversationMessageList} from '@/modules/workspace/Conversation/ConversationMessageList';
import {ConversationAssistantBar} from '@/modules/workspace/Conversation/ConversationAssistantBar';
import {AiDraftPanel} from '@/modules/workspace/Conversation/AiDraftPanel';
import {DraftPreparationError} from '@/modules/workspace/model/DraftPreparationError';
import {StatusNotice} from '@/shared/ui/StatusNotice/StatusNotice';
import {MessageEditDialog} from '@/modules/messages/MessageEditDialog/MessageEditDialog';
import type {Message} from '@/shared/api/client';
import type {Text as OutgoingText, GeneratedVoice, Reply, PreviewJobRequest} from '@aigram/api-client';
import {VoiceDraftPreview} from '@/modules/speech/VoiceDraftPreview/VoiceDraftPreview';

export function Conversation({
    chat,
    modelLabel,
    onMediaSent,
    onContextChanged,
    onMessageDeleted,
    onMessageUpdated,
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
    onVoiceSynthesis,
    onVoiceSend,
    voiceSynthesisModel,
    voiceSpeechRate,
    onVoiceSettings,
    onBack,
    onEarlier,
}: ConversationProps) {
    const t = useTranslations();
    const [today, setToday] = useState(() => messageDay(Date.now() / 1000));
    useEffect(() => {
        const timer = setInterval(() => setToday(messageDay(Date.now() / 1000)), 60000);
        return () => clearInterval(timer);
    }, []);
    const [input, setInput] = useState<{message: OutgoingText; richId: string}>();
    const [voiceDraft, setVoiceDraft] = useState<Reply & {voice: GeneratedVoice}>();
    const text = input?.message.text ?? '';
    const [richFailure, setRichFailure] = useState<{id: string; message: string}>();
    const richError = richFailure && richFailure.id === input?.richId ? richFailure.message : '';
    const [busy, setBusy] = useState<'send' | 'draft' | ''>('');
    const [localError, setLocalError] = useState('');
    const [editedMessage, setEditedMessage] = useState<Message>();
    const [draftNotice, setDraftNotice] = useState<{status: 'cancelled' | 'error'; description: string}>();
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
    const keepBottom = useCallback(() => {
        const area = scroll.current;
        if (area && stick.current && !contextMenus.current.size && !pagination.current) {
            const bottom = Math.max(0, area.scrollHeight - area.clientHeight);
            if (Math.abs(area.scrollTop - bottom) > 1) {
                area.scrollTop = bottom;
            }
        }
    }, []);

    useEffect(() => {
        if (!editedMessage) {
            return;
        }
        const messageId = editedMessage.id;
        menuOpenChanged(messageId, true);
        return () => menuOpenChanged(messageId, false);
    }, [editedMessage, menuOpenChanged]);

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
        keepBottom();
    }, [
        chat?.id,
        messages,
        activeReply?.id,
        activeReply?.thinking,
        activeReply?.phase,
        voiceDraft?.voice.id,
        keepBottom,
    ]);

    async function send() {
        if (!input || !text.trim() || busy || richError) {
            return;
        }
        setBusy('send');
        setLocalError('');
        setDraftNotice(undefined);
        try {
            await onSend(text, input.message.entities ?? []);
            setInput(undefined);
            stick.current = true;
        } catch (cause) {
            setLocalError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }

    const draft = useCallback(
        async (messageId?: string, output: PreviewJobRequest['output'] = 'text') => {
            if (busy) {
                return;
            }
            setBusy('draft');
            setLocalError('');
            setDraftNotice(undefined);
            try {
                const result =
                    output === 'voice' ? await onDraft(messageId, output) : await onDraft(messageId);
                if (output === 'voice') {
                    if (!result.voice) {
                        throw new Error(t('voice.generationFailed'));
                    }
                    setVoiceDraft({...result, voice: result.voice});
                    return;
                }
                setVoiceDraft(undefined);
                setInput({
                    message: {text: result.text, entities: result.entities ?? []},
                    richId: crypto.randomUUID(),
                });
            } catch (cause) {
                if (!(cause instanceof DOMException && cause.name === 'AbortError')) {
                    setDraftNotice({
                        status: cause instanceof DraftPreparationError ? cause.status : 'error',
                        description: errorMessage(cause),
                    });
                }
            } finally {
                setBusy('');
            }
        },
        [busy, onDraft, t],
    );

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
        <>
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
                        onResize={keepBottom}
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
                            <ConversationMessageList
                                messages={messages}
                                group={chat.kind === 'group' || chat.kind === 'supergroup'}
                                today={today}
                                onContextChanged={onContextChanged}
                                onDeleted={onMessageDeleted}
                                onEditRequest={onMessageUpdated ? setEditedMessage : undefined}
                                onMenuOpenChange={menuOpenChanged}
                                onDraftRequest={draft}
                            />
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
                    {draftNotice && (
                        <StatusNotice
                            className={styles.notice}
                            variant={draftNotice.status === 'cancelled' ? 'cancelled' : 'error'}
                            title={t(
                                draftNotice.status === 'cancelled'
                                    ? 'draftQueue.cancelled'
                                    : 'draftQueue.failed',
                            )}
                            description={
                                draftNotice.status === 'cancelled'
                                    ? t('draftQueue.cancelledDescription')
                                    : draftNotice.description === t('draftQueue.failed')
                                      ? t('draftQueue.failedDescription')
                                      : draftNotice.description
                            }
                            onClose={() => setDraftNotice(undefined)}
                        />
                    )}
                    {(error || localError) && (
                        <Text color="danger" role="alert">
                            {localError || error}
                        </Text>
                    )}
                    {voiceDraft && onVoiceSynthesis && onVoiceSend && (
                        <Box className={styles.voiceDraft}>
                            <VoiceDraftPreview
                                key={voiceDraft.voice.id}
                                draft={voiceDraft}
                                onSynthesize={async (text) => {
                                    setBusy('draft');
                                    try {
                                        return await onVoiceSynthesis(text);
                                    } finally {
                                        setBusy('');
                                    }
                                }}
                                onSend={async (id) => {
                                    setBusy('send');
                                    try {
                                        await onVoiceSend(id);
                                    } finally {
                                        setBusy('');
                                    }
                                }}
                                onDiscard={() => setVoiceDraft(undefined)}
                            />
                        </Box>
                    )}
                    {input && (
                        <AiDraftPanel
                            key={input.richId}
                            draft={{id: input.richId, message: input.message}}
                            busy={busy === 'send'}
                            error={richError}
                            onUpdate={(message) =>
                                setInput((previous) =>
                                    previous?.richId === input.richId ? {...previous, message} : previous,
                                )
                            }
                            onError={() =>
                                setRichFailure({id: input.richId, message: t('formattedDraftError')})
                            }
                            onSend={send}
                            onDiscard={() => setInput(undefined)}
                        />
                    )}
                    <ConversationAssistantBar
                        chatId={chat.id}
                        modelLabel={modelLabel ?? chat.rule.model}
                        busy={!!busy}
                        onDraft={(output) => draft(undefined, output)}
                        onMemory={onMemory}
                        voiceSynthesisModel={voiceSynthesisModel}
                        voiceSpeechRate={voiceSpeechRate}
                        onVoiceSettings={onVoiceSettings}
                        onMediaSent={onMediaSent ?? onRefresh}
                    />
                </Flex>
            </Flex>
            {editedMessage && onMessageUpdated && (
                <MessageEditDialog
                    key={`${editedMessage.chatId}:${editedMessage.id}`}
                    message={editedMessage}
                    onUpdated={onMessageUpdated}
                    onClose={() => setEditedMessage(undefined)}
                />
            )}
        </>
    );
}
