import {Box, Flex, Loader, Text as UiText} from '@gravity-ui/uikit';
import {lazy, Suspense, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {Text} from '@aigram/api-client';
import {editableMessage, outgoingMessage} from '@/modules/messages/MessageDraftEditor/messageDraft';

const MessageEditEditor = lazy(() =>
    import('@/modules/messages/MessageEditEditor/MessageEditEditor').then((module) => ({
        default: module.MessageEditEditor,
    })),
);

export function MessageDraftEditor({
    initialMessage,
    draftId,
    ariaLabel,
    disabled,
    onChange,
    onError,
    onSubmit,
}: {
    readonly initialMessage: Text;
    readonly draftId: string;
    readonly ariaLabel: string;
    readonly disabled?: boolean;
    readonly onChange: (message: Text) => void;
    readonly onError: () => void;
    readonly onSubmit?: () => Promise<void>;
}) {
    const t = useTranslations();
    const [initialDraft] = useState(() => editableMessage(initialMessage));
    return (
        <Box
            onKeyDownCapture={(event) => {
                if (
                    onSubmit &&
                    event.key === 'Enter' &&
                    !event.shiftKey &&
                    !event.nativeEvent.isComposing &&
                    event.target instanceof HTMLElement &&
                    event.target.closest('[contenteditable="true"]')
                ) {
                    event.preventDefault();
                    event.stopPropagation();
                    if (!disabled) {
                        void onSubmit();
                    }
                }
            }}
        >
            <Suspense
                fallback={
                    <Flex alignItems="center" gap={3} role="status">
                        <Loader size="s" />
                        <UiText>{t('formattedDraftLoading')}</UiText>
                    </Flex>
                }
            >
                <MessageEditEditor
                    initialDraft={initialDraft}
                    draftId={draftId}
                    ariaLabel={ariaLabel}
                    disabled={disabled}
                    autoFocus
                    compact
                    onChange={(draft) => onChange(outgoingMessage(draft))}
                    onError={onError}
                />
            </Suspense>
        </Box>
    );
}
