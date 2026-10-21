'use client';

import {Button, Flex, Loader, Text} from '@gravity-ui/uikit';
import {useSearchParams} from 'next/navigation';
import {useCallback, useEffect, useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {Message} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {validMediaAddress} from '@/modules/media/model/gallery';
import {MediaViewer} from '@/modules/media/MediaViewer/MediaViewer';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import styles from '@/modules/media/MediaViewer/MediaViewer.module.css';

export function MediaViewerPage() {
    const params = useSearchParams();
    const chatId = params.get('chatId') ?? '';
    const messageId = params.get('messageId') ?? '';
    const t = useMediaMessages();
    const [messages, setMessages] = useState<Message[]>();
    const [error, setError] = useState('');
    const [retry, setRetry] = useState(0);
    const close = useCallback(() => {
        const bridge = desktopBridge();
        if (bridge?.closeMediaViewer) {
            void bridge.closeMediaViewer().catch((cause) => setError(errorMessage(cause)));
        } else {
            window.close();
        }
    }, []);

    useEffect(() => {
        if (!validMediaAddress(chatId, messageId)) {
            return;
        }
        const controller = new AbortController();
        void api
            .GET('/api/chats/{chatId}/gallery', {
                params: {path: {chatId}, query: {around: messageId}},
                signal: controller.signal,
            })
            .then(dataOrThrow)
            .then((gallery) => {
                if (!controller.signal.aborted) {
                    setMessages(gallery.messages);
                }
            })
            .catch((cause) => {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            });
        return () => controller.abort();
    }, [chatId, messageId, retry]);

    if (messages) {
        return <MediaViewer key={messageId} messages={messages} initialId={messageId} onClose={close} />;
    }
    return (
        <Flex direction="column" alignItems="center" justifyContent="center" gap={4} className={styles.root}>
            {error || !validMediaAddress(chatId, messageId) ? (
                <>
                    <Text variant="subheader-2" role="alert">
                        {error || t.invalid}
                    </Text>
                    {validMediaAddress(chatId, messageId) && (
                        <Button
                            onClick={() => {
                                setError('');
                                setRetry((value) => value + 1);
                            }}
                        >
                            {t.retry}
                        </Button>
                    )}
                </>
            ) : (
                <>
                    <Loader size="m" />
                    <Text>{t.loading}</Text>
                </>
            )}
            <Button view="flat-secondary" onClick={close}>
                {t.close}
            </Button>
        </Flex>
    );
}
