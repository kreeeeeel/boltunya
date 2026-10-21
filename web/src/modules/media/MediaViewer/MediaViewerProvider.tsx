import {createContext, useContext, useState} from 'react';
import type {ReactNode} from 'react';
import {errorMessage} from '@/shared/api/client';
import type {Message} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import {MediaViewerOverlay} from '@/modules/media/MediaViewer/MediaViewerOverlay';

const MediaViewerContext = createContext<((message: Message) => void) | undefined>(undefined);

export function useMediaViewer() {
    return useContext(MediaViewerContext);
}

export function MediaViewerProvider({
    messages,
    children,
}: {
    readonly messages: Message[];
    readonly children: ReactNode;
}) {
    const [selected, setSelected] = useState<Message>();
    const [error, setError] = useState('');
    async function open(message: Message) {
        setError('');
        const desktop = desktopBridge();
        if (desktop?.openMediaViewer) {
            try {
                await desktop.openMediaViewer({chatId: message.chatId, messageId: message.id});
                return;
            } catch (cause) {
                setError(errorMessage(cause));
            }
        }
        setSelected(message);
    }
    return (
        <MediaViewerContext value={(message) => void open(message)}>
            {children}
            {selected && (
                <MediaViewerOverlay
                    key={selected.id}
                    messages={[selected, ...messages]}
                    initialId={selected.id}
                    error={error}
                    onClose={() => setSelected(undefined)}
                />
            )}
        </MediaViewerContext>
    );
}
