import {Modal, Text} from '@gravity-ui/uikit';
import type {Message} from '@/shared/api/client';
import {MediaViewer} from '@/modules/media/MediaViewer/MediaViewer';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import styles from '@/modules/media/MediaViewer/MediaViewer.module.css';

export function MediaViewerOverlay({
    messages,
    initialId,
    onClose,
    error,
}: {
    readonly messages: Message[];
    readonly initialId: string;
    readonly onClose: () => void;
    readonly error?: string;
}) {
    const t = useMediaMessages();
    return (
        <Modal
            open
            onClose={onClose}
            className={styles.modal}
            contentClassName={styles.modalContent}
            aria-label={t.open}
        >
            {error && (
                <Text role="alert" className={styles.nativeError}>
                    {error}
                </Text>
            )}
            <MediaViewer messages={messages} initialId={initialId} onClose={onClose} />
        </Modal>
    );
}
