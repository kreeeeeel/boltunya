import {Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {ArrowDownToLine} from '@gravity-ui/icons';
import {useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage, type Message} from '@/shared/api/client';
import styles from '@/modules/workspace/MessageMedia/MessageMedia.module.css';

export function DeferredMedia({
    media,
    onDownloaded,
}: {
    readonly media: NonNullable<Message['media']>;
    readonly onDownloaded: () => void;
}) {
    const t = useTranslations();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    async function download() {
        setBusy(true);
        setError('');
        try {
            dataOrThrow(
                await api.POST('/api/media/{fileId}/download', {params: {path: {fileId: media.fileId}}}),
            );
            onDownloaded();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Flex direction="column" gap={2} className={styles.deferred}>
            <Text>{media.fileName}</Text>
            <Button loading={busy} onClick={() => void download()}>
                <Icon data={ArrowDownToLine} size={16} />
                {t('storage.download')}
            </Button>
            {error && <Text color="danger">{error}</Text>}
        </Flex>
    );
}
