import {Box, Button, DropdownMenu, Flex, Icon, Text, TextArea, Tooltip} from '@gravity-ui/uikit';
import {FaceSmile, File, Paperclip, PaperPlane, Picture, Sparkles, Sticker, Video} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api} from '@/shared/api/client';
import {AttachmentDialog} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import type {AttachmentSelection} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import {StickerPicker} from '@/modules/media/StickerPicker/StickerPicker';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import {validAttachments} from '@/modules/media/model/uploads';
import type {AttachmentKind} from '@/modules/media/model/uploads';
import {EmojiPicker} from '@/shared/ui/EmojiPicker/EmojiPicker';
import styles from '@/modules/workspace/Conversation/Conversation.module.css';
const MAX_MESSAGE_LENGTH = 4096;
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
    const chatUi = useTranslations('chatUi');
    const media = useMediaMessages();
    const [error, setError] = useState('');
    const [selection, setSelection] = useState<AttachmentSelection[]>();
    const [kind, setKind] = useState<AttachmentKind>('media');
    const [stickers, setStickers] = useState(false);
    const files = useRef<HTMLInputElement>(null);
    const input = useRef<HTMLTextAreaElement>(null);
    const [stickerAnchor, setStickerAnchor] = useState<HTMLButtonElement | null>(null);
    const urls = useRef(new Set<string>());
    useEffect(() => {
        const values = urls.current;
        return () => values.forEach(URL.revokeObjectURL);
    }, []);
    function choose(mode: 'photo' | 'video' | 'document') {
        setKind(mode === 'document' ? 'document' : 'media');
        setError('');
        if (files.current) {
            files.current.accept =
                mode === 'photo'
                    ? 'image/jpeg,image/png,image/webp'
                    : mode === 'video'
                      ? 'video/mp4,video/webm'
                      : '';
            files.current.click();
        }
    }
    function insertEmoji(emoji: string) {
        const start = input.current?.selectionStart ?? text.length;
        const end = input.current?.selectionEnd ?? start;
        const next = text.slice(0, start) + emoji + text.slice(end);
        if (next.length > MAX_MESSAGE_LENGTH) {
            return;
        }
        onTextChange(next);
        requestAnimationFrame(() => {
            input.current?.focus({preventScroll: true});
            input.current?.setSelectionRange(start + emoji.length, start + emoji.length);
        });
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
                        {
                            text: chatUi('photos'),
                            iconStart: <Icon data={Picture} size={18} />,
                            action: () => choose('photo'),
                        },
                        {
                            text: chatUi('videos'),
                            iconStart: <Icon data={Video} size={18} />,
                            action: () => choose('video'),
                        },
                        {
                            text: media.documents,
                            iconStart: <Icon data={File} size={18} />,
                            action: () => choose('document'),
                        },
                    ]}
                    popupProps={{className: styles.attachmentMenu, placement: ['top-start', 'bottom-start']}}
                    renderSwitcher={(props) => (
                        <Button
                            {...props}
                            className={styles.composerButton}
                            view="flat-secondary"
                            disabled={busy === 'send'}
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
                    controlRef={input}
                    view="clear"
                    size="l"
                    className={styles.input}
                    value={text}
                    disabled={busy === 'send'}
                    onUpdate={onTextChange}
                    minRows={1}
                    maxRows={5}
                    placeholder={t('messagePlaceholder')}
                    controlProps={{'aria-label': t('messagePlaceholder'), maxLength: MAX_MESSAGE_LENGTH}}
                    onKeyDown={(event) => {
                        if (event.key === 'Enter' && !event.shiftKey) {
                            event.preventDefault();
                            void onSend();
                        }
                    }}
                />
                <EmojiPicker
                    value=""
                    trigger={<Icon data={FaceSmile} size={20} />}
                    className={styles.composerButton}
                    disabled={busy === 'send'}
                    placement="top-end"
                    returnFocus={false}
                    onUpdate={insertEmoji}
                />
                <Button
                    ref={setStickerAnchor}
                    className={styles.composerButton}
                    view="flat-secondary"
                    aria-label={media.stickers}
                    disabled={busy === 'send'}
                    onClick={() => setStickers((value) => !value)}
                >
                    <Icon data={Sticker} size={20} />
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
                        await api.chats.sendSticker({
                            chatId,
                            sendStickerRequest: {stickerId},
                        });
                        await onMediaSent();
                    }}
                />
            )}
        </Box>
    );
}
