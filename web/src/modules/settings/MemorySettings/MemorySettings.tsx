import {Button, Icon, Text} from '@gravity-ui/uikit';
import {Check} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';

import {SettingsSection} from '@/modules/settings/SettingsSection/SettingsSection';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import {MemoryContent} from '@/modules/memory/MemoryContent/MemoryContent';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';

export function MemorySettings() {
    const t = useTranslations();
    const memory = useMemoryDocument('');
    const feedback = useSaveFeedback(memory.notes);
    async function save() {
        feedback.clearSaved();
        if (await memory.save()) {
            feedback.markSaved();
        }
    }
    return (
        <SettingsSection
            id="memory"
            actions={
                <Button
                    size="l"
                    view="action"
                    loading={memory.busy}
                    disabled={!memory.document || memory.notes === memory.document.notes}
                    aria-label={t(feedback.saved ? 'saved' : 'memory.save')}
                    onClick={() => void save()}
                >
                    {feedback.saved && <Icon data={Check} size={18} />}
                    <Text role={feedback.saved ? 'status' : undefined}>
                        {t(feedback.saved ? 'saved' : 'memory.save')}
                    </Text>
                </Button>
            }
        >
            <MemoryContent memory={memory} />
        </SettingsSection>
    );
}
