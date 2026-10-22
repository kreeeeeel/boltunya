import {Fragment, memo, useMemo} from 'react';
import {Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import type {Message} from '@/shared/api/client';
import type {ContextExclusionChange} from '@/modules/messages/model/ContextExclusionChange';
import type {MessageDeletionResult} from '@/modules/messages/MessageDeletionDialog/MessageDeletionDialog.types';
import {messageDay} from '@/modules/workspace/model/messagePresentation';
import {MessageBubble} from '@/modules/workspace/Conversation/MessageBubble';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';

export const ConversationMessageList = memo(function ConversationMessageList({
    messages,
    group,
    today,
    onContextChanged,
    onDeleted,
    onEditRequest,
    onMenuOpenChange,
    onDraftRequest,
}: {
    readonly messages: Message[];
    readonly group: boolean;
    readonly today: string;
    readonly onContextChanged?: (change: ContextExclusionChange) => Promise<void>;
    readonly onDeleted?: (result: MessageDeletionResult) => Promise<void>;
    readonly onEditRequest?: (message: Message) => void;
    readonly onMenuOpenChange: (messageId: string, open: boolean) => void;
    readonly onDraftRequest: (messageId: string, output: 'text' | 'voice') => Promise<void>;
}) {
    const t = useTranslations();
    const locale = useLocale();
    const replies = useMemo(() => new Map(messages.map((message) => [message.id, message.text])), [messages]);
    const dateFormat = useMemo(
        () => new Intl.DateTimeFormat(locale, {day: 'numeric', month: 'long'}),
        [locale],
    );
    const timeFormat = useMemo(
        () => new Intl.DateTimeFormat(locale, {hour: '2-digit', minute: '2-digit'}),
        [locale],
    );

    return (
        <>
            {messages.map((message, index) => {
                const day = messageDay(message.date);
                return (
                    <Fragment key={message.id}>
                        {(index === 0 || day !== messageDay(messages[index - 1].date)) && (
                            <Text className={styles.date} qa="message-date">
                                {day === today ? t('today') : dateFormat.format(message.date * 1000)}
                            </Text>
                        )}
                        <MessageBubble
                            message={message}
                            nextMessage={messages[index + 1]}
                            previousMessage={messages[index - 1]}
                            group={group || message.group}
                            replyText={replies.get(message.replyToId ?? '')}
                            timeFormat={timeFormat}
                            onContextChanged={onContextChanged}
                            onDeleted={onDeleted}
                            onEditRequest={onEditRequest}
                            onMenuOpenChange={onMenuOpenChange}
                            onDraftRequest={message.outgoing ? undefined : onDraftRequest}
                        />
                    </Fragment>
                );
            })}
        </>
    );
});
