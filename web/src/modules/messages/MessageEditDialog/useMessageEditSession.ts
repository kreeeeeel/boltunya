import {useCallback, useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import type {MessageEditOptions} from '@aigram/api-client';
import {api} from '@/shared/api/client';
import type {Message} from '@/shared/api/client';
import type {MessageEditDraft} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';
import {
    editDraftFingerprint,
    messageEditDraft,
    messageEditLength,
} from '@/modules/messages/MessageEditDialog/messageEditDraft';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import {messageEditFailure} from '@/modules/messages/MessageEditDialog/messageEditFailure';

export function useMessageEditSession(
    chatId: string,
    messageId: string,
    onUpdated: (message: Message) => void,
) {
    const t = useTranslations('messageEdit');
    const [options, setOptions] = useState<MessageEditOptions>();
    const [draft, setDraft] = useState<MessageEditDraft>();
    const [initial, setInitial] = useState<MessageEditDraft>();
    const [version, setVersion] = useState(0);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [needsRefresh, setNeedsRefresh] = useState(false);
    const [conflict, setConflict] = useState(false);
    const [unavailable, setUnavailable] = useState(false);
    const [readError, setReadError] = useState('');
    const [saveError, setSaveError] = useState('');
    const [editorError, setEditorError] = useState('');
    const fingerprint = draft ? editDraftFingerprint(draft) : '';
    const feedback = useSaveFeedback(fingerprint);
    const mounted = useRef(false);
    const read = useRef<AbortController | null>(null);
    const saving = useRef(false);

    const loadOptions = useCallback(() => {
        read.current?.abort();
        const controller = new AbortController();
        read.current = controller;
        return api.chats
            .messageEditOptions({chatId, messageId}, {signal: controller.signal})
            .then((fresh) => {
                if (!mounted.current || controller.signal.aborted) {
                    return;
                }
                const next = messageEditDraft(fresh);
                setOptions(fresh);
                setInitial(next);
                setDraft(next);
                setVersion((previous) => previous + 1);
                setConflict(false);
                setUnavailable(false);
                setSaveError('');
                setEditorError('');
                setNeedsRefresh(false);
                return next;
            })
            .catch((cause: unknown) => {
                if (mounted.current && !controller.signal.aborted) {
                    setReadError(messageEditFailure(cause, 'load'));
                }
            })
            .finally(() => {
                if (mounted.current && !controller.signal.aborted) {
                    setLoading(false);
                    setRefreshing(false);
                }
            });
    }, [chatId, messageId]);

    function reload() {
        setRefreshing(true);
        setReadError('');
        return loadOptions();
    }

    useEffect(() => {
        mounted.current = true;
        void loadOptions();
        return () => {
            mounted.current = false;
            read.current?.abort();
        };
    }, [loadOptions]);

    const dirty = !!draft && !!initial && fingerprint !== editDraftFingerprint(initial);
    const validation =
        options && draft
            ? messageEditLength(draft.text) > options.maxLength
                ? t('tooLong', {max: options.maxLength})
                : options.kind === 'text' && draft.text.trim().length === 0
                  ? t('emptyText')
                  : ''
            : '';

    async function save() {
        if (
            saving.current ||
            refreshing ||
            needsRefresh ||
            conflict ||
            unavailable ||
            editorError ||
            validation ||
            !dirty ||
            !draft ||
            !options
        ) {
            return;
        }
        saving.current = true;
        setBusy(true);
        setSaveError('');
        feedback.clearSaved();
        let updated: Message;
        try {
            updated = await api.chats.editMessage({
                chatId,
                messageId,
                messageEditRequest: {...draft, revision: options.revision},
            });
        } catch (cause) {
            if (mounted.current) {
                const failure = messageEditFailure(cause, 'save');
                setConflict(failure === 'changedElsewhere');
                setUnavailable(failure === 'unavailable');
                setSaveError(failure);
                setBusy(false);
            }
            saving.current = false;
            return;
        }

        onUpdated(updated);
        saving.current = false;
        if (!mounted.current) {
            return;
        }
        setBusy(false);
        setNeedsRefresh(true);
        feedback.markValueSaved(fingerprint);
        const fresh = await reload();
        if (fresh && mounted.current) {
            feedback.markValueSaved(editDraftFingerprint(fresh));
        }
    }

    return {
        options,
        initial,
        draft,
        setDraft,
        version,
        dirty,
        loading,
        busy,
        refreshing,
        needsRefresh,
        conflict,
        unavailable,
        readError: readError ? t(readError, {max: options?.maxLength ?? 0}) : '',
        saveError: saveError ? t(saveError, {max: options?.maxLength ?? 0}) : '',
        editorError,
        setEditorError,
        validation,
        saved: feedback.saved,
        reload,
        save,
    };
}
