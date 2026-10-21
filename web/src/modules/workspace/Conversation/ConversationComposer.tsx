import {Box, Button, DropdownMenu, Flex, Icon, Text, TextArea, Tooltip} from '@gravity-ui/uikit';
import {FaceSmile, Paperclip, PaperPlane, Sparkles} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow} from '@/shared/api/client';
import {AttachmentDialog} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import type {AttachmentSelection} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import {StickerPicker} from '@/modules/media/StickerPicker/StickerPicker';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import {validAttachments} from '@/modules/media/model/uploads';
import type {AttachmentKind} from '@/modules/media/model/uploads';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';

export function ConversationComposer({
    chatId,
    text,
    onTextChange,
    busy,
    onSend,
    onDraft,
    onMediaSent,
}: {
    readonly chatId: string;
    readonly text: string;
    readonly onTextChange: (text: string) => void;
    readonly busy: 'send' | 'draft' | '';
    readonly onSend: () => Promise<void>;
    readonly onDraft: () => Promise<void>;
    readonly onMediaSent: () => Promise<void>;
}) {
    const t = useTranslations();
    const media = useMediaMessages();
    const [error, setError] = useState('');
    const [selection, setSelection] = useState<AttachmentSelection[]>();
    const [kind, setKind] = useState<AttachmentKind>('media');
    const [stickers, setStickers] = useState(false);
    const files = useRef<HTMLInputElement>(null);
    const [stickerAnchor, setStickerAnchor] = useState<HTMLButtonElement | null>(null);
    const urls = useRef(new Set<string>());

    useEffect(() => {
        const values = urls.current;
        return () => values.forEach(URL.revokeObjectURL);
    }, []);
    function choose(mode: AttachmentKind) {
        setKind(mode);
        setError('');
        if (files.current) {
            files.current.accept =
                mode === 'media' ? 'image/jpeg,image/png,image/webp,video/mp4,video/webm' : '';
            files.current.click();
        }
    }

    function closeAttachments() {
        selection?.forEach((item) => {
            URL.revokeObjectURL(item.preview);
            urls.current.delete(item.preview);
        });
        setSelection(undefined);
    }

    return (
        <Box className={styles.composerArea}>
            {error && (
                <Text color="danger" role="alert">
                    {error}
                </Text>
            )}
            <Flex alignItems="center" gap={1} className={styles.composer} qa="message-composer">
                <Box
                    as="input"
                    type="file"
                    ref={files}
                    multiple
                    hidden
                    onChange={(event) => {
                        const chosen = Array.from(files.current?.files ?? []);
                        if (files.current) {
                            files.current.value = '';
                        }
                        const invalid = validAttachments(chosen, kind);
                        if (invalid) {
                            if (chosen.length) {
                                setError(invalid === 'unsupported' ? t('unsupportedMedia') : media[invalid]);
                            }
                            return;
                        }
                        setSelection(
                            chosen.map((file) => {
                                const preview = kind === 'media' ? URL.createObjectURL(file) : '';
                                if (preview) {
                                    urls.current.add(preview);
                                }
                                return {file, preview};
                            }),
                        );
                        event.stopPropagation();
                    }}
                />
                <DropdownMenu
                    items={[
                        {text: media.photos, action: () => choose('media')},
                        {text: media.documents, action: () => choose('document')},
                    ]}
                    renderSwitcher={(props) => (
                        <Button
                            {...props}
                            className={styles.composerButton}
                            view="flat-secondary"
                            aria-label={media.attach}
                        >
                            <Icon data={Paperclip} size={20} />
                        </Button>
                    )}
                />
                <Tooltip content={t('draft')}>
                    <Button
                        className={styles.composerButton}
                        view="flat-secondary"
                        loading={busy === 'draft'}
                        disabled={!!busy}
                        onClick={() => void onDraft()}
                        aria-label={t('draft')}
                    >
                        <Icon data={Sparkles} size={19} />
                    </Button>
                </Tooltip>
                <TextArea
                    view="clear"
                    size="l"
                    className={styles.input}
                    value={text}
                    disabled={busy === 'send'}
                    onUpdate={onTextChange}
                    minRows={1}
                    maxRows={5}
                    placeholder={t('messagePlaceholder')}
                    controlProps={{'aria-label': t('messagePlaceholder'), maxLength: 4096}}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && !event.shiftKey) {
                            event.preventDefault();
                            void onSend();
                        }
                    }}
                />
                <Button
                    ref={setStickerAnchor}
                    className={styles.composerButton}
                    view="flat-secondary"
                    aria-label={media.stickers}
                    onClick={() => setStickers((value) => !value)}
                >
                    <Icon data={FaceSmile} size={20} />
                </Button>
                <Button
                    view="action"
                    size="m"
                    loading={busy === 'send'}
                    disabled={!text.trim() || busy === 'send'}
                    onClick={() => void onSend()}
                    aria-label={t('send')}
                    className={styles.composerButton}
                >
                    <Icon data={PaperPlane} size={18} />
                </Button>
            </Flex>
            {selection && (
                <AttachmentDialog
                    chatId={chatId}
                    selection={selection}
                    kind={kind}
                    onClose={closeAttachments}
                    onSent={onMediaSent}
                />
            )}
            {stickers && (
                <StickerPicker
                    anchor={stickerAnchor}
                    onClose={() => setStickers(false)}
                    onPick={async (stickerId) => {
                        dataOrThrow(
                            await api.POST('/api/chats/{chatId}/stickers', {
                                params: {path: {chatId}},
                                body: {stickerId},
                            }),
                        );
                        await onMediaSent();
                    }}
                />
            )}
        </Box>
    );
}
