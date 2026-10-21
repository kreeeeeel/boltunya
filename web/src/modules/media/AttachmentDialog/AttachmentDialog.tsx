import {Box, Button, Dialog, Flex, Icon, Progress, Text, TextArea} from '@gravity-ui/uikit';
import {File as FileIcon, Xmark} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import {uploadFile} from '@/modules/media/model/uploads';
import type {AttachmentKind, UploadedFile} from '@/modules/media/model/uploads';
import styles from '@/modules/media/AttachmentDialog/AttachmentDialog.module.css';

export interface AttachmentSelection {
    file: File;
    preview: string;
}

export function AttachmentDialog({
    chatId,
    selection,
    kind,
    onClose,
    onSent,
}: {
    readonly chatId: string;
    readonly selection: AttachmentSelection[];
    readonly kind: AttachmentKind;
    readonly onClose: () => void;
    readonly onSent: () => Promise<void>;
}) {
    const t = useMediaMessages();
    const [items, setItems] = useState(selection);
    const [caption, setCaption] = useState('');
    const [busy, setBusy] = useState(false);
    const [sending, setSending] = useState(false);
    const [progress, setProgress] = useState(0);
    const [error, setError] = useState('');
    const [sent, setSent] = useState(false);
    const controller = useRef<AbortController | null>(null);
    const uploaded = useRef<Map<File, UploadedFile>>(new Map());
    const delivered = useRef(false);
    const submitted = useRef(false);

    useEffect(() => {
        const pending = uploaded.current;
        return () => {
            controller.current?.abort();
            if (!delivered.current && !submitted.current) {
                pending.forEach((item) => {
                    void api.DELETE('/api/uploads/{id}', {params: {path: {id: item.id}}});
                });
            }
        };
    }, []);

    async function send() {
        if (sent) {
            onClose();
            return;
        }
        if (busy || !items.length) {
            return;
        }
        setBusy(true);
        setError('');
        const abort = new AbortController();
        controller.current = abort;
        const total = items.reduce((sum, item) => sum + item.file.size, 0);
        let loaded = 0;
        try {
            for (const item of items) {
                if (!uploaded.current.has(item.file)) {
                    const completed = loaded;
                    const result = await uploadFile(item.file, abort.signal, (bytes) =>
                        setProgress(((completed + bytes) / total) * 100),
                    );
                    uploaded.current.set(item.file, result);
                }
                loaded += item.file.size;
                setProgress((loaded / total) * 100);
            }
            setSending(true);
            submitted.current = true;
            dataOrThrow(
                await api.POST('/api/chats/{chatId}/attachments', {
                    params: {path: {chatId}},
                    body: {
                        uploadIds: items.map((item) => uploaded.current.get(item.file)!.id),
                        kind,
                        caption,
                    },
                }),
            );
            delivered.current = true;
            setSent(true);
            try {
                await onSent();
                onClose();
            } catch (cause) {
                setError(errorMessage(cause));
            }
        } catch (cause) {
            if (!abort.signal.aborted) {
                setError(errorMessage(cause));
            }
        } finally {
            setBusy(false);
            setSending(false);
            controller.current = null;
        }
    }

    return (
        <Dialog
            open
            onClose={() => {
                if (!sending) {
                    controller.current?.abort();
                    onClose();
                }
            }}
            maxWidth="s"
            aria-label={kind === 'document' ? t.documents : t.photos}
        >
            <Dialog.Header caption={kind === 'document' ? t.documents : t.photos} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Box className={styles.grid}>
                        {items.map((item, index) => (
                            <Box key={`${item.file.name}:${index}`} className={styles.item}>
                                {item.preview ? (
                                    item.file.type.startsWith('video/') ? (
                                        <Box
                                            as="video"
                                            src={item.preview}
                                            preload="metadata"
                                            muted
                                            className={styles.preview}
                                        />
                                    ) : (
                                        <Box
                                            as="img"
                                            src={item.preview}
                                            alt={item.file.name}
                                            className={styles.preview}
                                        />
                                    )
                                ) : (
                                    <Flex className={styles.document} alignItems="center" gap={2}>
                                        <Icon data={FileIcon} size={24} />
                                        <Text ellipsis>{item.file.name}</Text>
                                    </Flex>
                                )}
                                {!busy && !sent && (
                                    <Button
                                        className={styles.remove}
                                        view="flat-secondary"
                                        size="s"
                                        aria-label={`${t.remove}: ${item.file.name}`}
                                        onClick={() => {
                                            setItems((previous) =>
                                                previous.filter((_, current) => current !== index),
                                            );
                                        }}
                                    >
                                        <Icon data={Xmark} size={16} />
                                    </Button>
                                )}
                                <Text className={styles.name} ellipsis>
                                    {item.file.name}
                                </Text>
                            </Box>
                        ))}
                    </Box>
                    <TextArea
                        value={caption}
                        onUpdate={setCaption}
                        disabled={busy || sent}
                        minRows={2}
                        maxRows={4}
                        placeholder={t.caption}
                        controlProps={{maxLength: 1024, 'aria-label': t.caption}}
                    />
                    {busy && (
                        <Flex direction="column" gap={2}>
                            <Text>{sending ? t.sending : t.uploading}</Text>
                            <Progress value={progress} />
                        </Flex>
                    )}
                    {error && (
                        <Text role="alert" color="danger">
                            {error}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={sent ? t.close : t.send}
                textButtonCancel={t.cancel}
                loading={busy}
                propsButtonApply={{disabled: !items.length || busy}}
                propsButtonCancel={{disabled: sending}}
                onClickButtonApply={() => void send()}
                onClickButtonCancel={() => {
                    controller.current?.abort();
                    onClose();
                }}
            />
        </Dialog>
    );
}
