import {Box, Button, DropdownMenu, Flex, Icon, Text, TextArea, Tooltip} from '@gravity-ui/uikit';
import {
    File,
    Paperclip,
    PaperPlane,
    Picture,
    Sparkles,
    Video,
    Microphone,
    Text as TextIcon,
} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {Text as OutgoingText} from '@aigram/api-client';
import type {PreviewJobRequest} from '@aigram/api-client';
import {useSpeechState} from '@/modules/speech/model/useSpeechState';
import {speechReady} from '@/modules/speech/model/speechReady';
import {MessageDraftEditor} from '@/modules/messages/MessageDraftEditor/MessageDraftEditor';
import {AttachmentDialog} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import type {AttachmentSelection} from '@/modules/media/AttachmentDialog/AttachmentDialog';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import {validAttachments} from '@/modules/media/model/uploads';
import type {AttachmentKind} from '@/modules/media/model/uploads';
import {VoiceComposeDialog} from '@/modules/speech/VoiceComposeDialog/VoiceComposeDialog';
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
    richDraft,
    richError = '',
    onRichChange,
    onRichError,
    onRichConsumed,
    voiceSynthesisModel = 'piper-ru-irina',
    voiceSpeechRate = 1,
    onVoiceSettings,
}: {
    readonly chatId: string;
    readonly text: string;
    readonly onTextChange: (text: string) => void;
    readonly busy: 'send' | 'draft' | '';
    readonly onSend: () => Promise<void>;
    readonly onDraft: (output?: PreviewJobRequest['output']) => Promise<void>;
    readonly onMediaSent: () => Promise<void>;
    readonly richDraft?: {id: string; message: OutgoingText};
    readonly richError?: string;
    readonly onRichChange?: (id: string, message: OutgoingText) => void;
    readonly onRichError?: (id: string) => void;
    readonly onRichConsumed?: (id: string) => void;
    readonly voiceSynthesisModel?: string;
    readonly voiceSpeechRate?: number;
    readonly onVoiceSettings?: () => void;
}) {
    const t = useTranslations();
    const chatUi = useTranslations('chatUi');
    const media = useMediaMessages();
    const [draftMenuOpened, setDraftMenuOpened] = useState(false);
    const speech = useSpeechState(draftMenuOpened);
    const voiceReady = speechReady(speech.state?.assets ?? [], voiceSynthesisModel);
    const [error, setError] = useState('');
    const [selection, setSelection] = useState<AttachmentSelection[]>();
    const [attachmentDraft, setAttachmentDraft] = useState<typeof richDraft>();
    const [readyRichId, setReadyRichId] = useState<string>();
    const [kind, setKind] = useState<AttachmentKind>('media');
    const [voiceComposeOpened, setVoiceComposeOpened] = useState(false);
    const files = useRef<HTMLInputElement>(null);
    const urls = useRef(new Set<string>());
    const richTooLong = !!richDraft && Array.from(text).length > MAX_MESSAGE_LENGTH;
    const sendDisabled =
        busy === 'send' || !!richError || richTooLong || (!!richDraft && readyRichId !== richDraft.id);
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
            {(richError || richTooLong) && (
                <Text color="danger" role="alert">
                    {richError || t('messageEdit.tooLong', {max: MAX_MESSAGE_LENGTH})}
                </Text>
            )}
            {richDraft && (
                <Box className={styles.richDraft}>
                    <MessageDraftEditor
                        key={richDraft.id}
                        initialMessage={richDraft.message}
                        draftId={richDraft.id}
                        ariaLabel={t('messagePlaceholder')}
                        disabled={busy === 'send' || !!richError}
                        onChange={(message) => {
                            setReadyRichId(richDraft.id);
                            onRichChange?.(richDraft.id, message);
                        }}
                        onError={() => onRichError?.(richDraft.id)}
                        onSubmit={sendDisabled ? undefined : onSend}
                    />
                </Box>
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
                        setAttachmentDraft(richDraft);
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
                            disabled={busy === 'send' || !!richError}
                            aria-label={media.attach}
                        >
                            <Icon data={Paperclip} size={20} />
                        </Button>
                    )}
                />
                <DropdownMenu
                    onOpenToggle={(open) => {
                        if (open) {
                            setDraftMenuOpened(true);
                        }
                    }}
                    items={[
                        {
                            text: t('voice.generateText'),
                            iconStart: <Icon data={TextIcon} size={18} />,
                            action: () => void onDraft('text'),
                        },
                        {
                            text: t('voice.generateVoice'),
                            iconStart: <Icon data={Microphone} size={18} />,
                            disabled: !voiceReady,
                            title: voiceReady ? undefined : t('voice.modelsRequired'),
                            action: () => void onDraft('voice'),
                        },
                        ...(!voiceReady && onVoiceSettings
                            ? [{text: t('voice.openSettings'), action: onVoiceSettings}]
                            : []),
                    ]}
                    popupProps={{placement: ['top-start', 'bottom-start'], className: styles.attachmentMenu}}
                    renderSwitcher={(props) => (
                        <Tooltip content={t('draft')}>
                            <Button
                                {...props}
                                className={styles.composerButton}
                                view="flat-secondary"
                                loading={busy === 'draft'}
                                disabled={!!busy}
                                aria-label={t('draft')}
                            >
                                <Icon data={Sparkles} size={19} />
                            </Button>
                        </Tooltip>
                    )}
                />
                {richDraft ? (
                    <Box className={styles.input} />
                ) : (
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
                        controlProps={{'aria-label': t('messagePlaceholder'), maxLength: MAX_MESSAGE_LENGTH}}
                        onKeyDown={(event) => {
                            if (event.key === 'Enter' && !event.shiftKey) {
                                event.preventDefault();
                                void onSend();
                            }
                        }}
                    />
                )}
                <Button
                    className={styles.composerButton}
                    view="flat-secondary"
                    aria-label={t('voice.manualTitle')}
                    disabled={busy === 'send'}
                    onClick={() => setVoiceComposeOpened(true)}
                >
                    <Icon data={Microphone} size={20} />
                </Button>
                <Button
                    view="action"
                    size="m"
                    loading={busy === 'send'}
                    disabled={!text.trim() || sendDisabled}
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
                    initialCaption={attachmentDraft?.message}
                    onClose={closeAttachments}
                    onSent={async () => {
                        if (attachmentDraft) {
                            onRichConsumed?.(attachmentDraft.id);
                        }
                        await onMediaSent();
                    }}
                />
            )}
            {voiceComposeOpened && (
                <VoiceComposeDialog
                    chatId={chatId}
                    defaultModelId={voiceSynthesisModel}
                    defaultSpeed={voiceSpeechRate}
                    onClose={() => setVoiceComposeOpened(false)}
                    onSent={onMediaSent}
                    onSettings={onVoiceSettings}
                />
            )}
        </Box>
    );
}
