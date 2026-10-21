'use client';
import {Dialog} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import {MemoryContent} from '@/modules/memory/MemoryContent/MemoryContent';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
export function MemoryDialog({
    chatId = '',
    title = '',
    onClose,
}: {
    readonly chatId?: string;
    readonly title?: string;
    readonly onClose: () => void;
}) {
    const t = useTranslations();
    const memory = useMemoryDocument(chatId);
    const feedback = useSaveFeedback(memory.notes);
    async function save() {
        feedback.clearSaved();
        if (await memory.save()) {
            feedback.markSaved();
        }
    }
    return (
        <Dialog
            open
            contentOverflow="auto"
            maxWidth="s"
            fullWidth
            onClose={onClose}
            aria-label={t(chatId ? 'memory.chatTitle' : 'memory.title', {name: title})}
        >
            <Dialog.Header caption={t(chatId ? 'memory.chatTitle' : 'memory.title', {name: title})} />
            <Dialog.Body>
                <MemoryContent memory={memory} chatId={chatId} />
            </Dialog.Body>
            <SaveDialogFooter
                saved={feedback.saved}
                saveLabel={t('memory.save')}
                cancelLabel={t('close')}
                onSave={() => void save()}
                onClose={onClose}
                busy={memory.busy}
                disabled={!memory.document || memory.notes === memory.document.notes}
            />
        </Dialog>
    );
}
