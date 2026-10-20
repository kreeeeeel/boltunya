import {Check} from '@gravity-ui/icons';
import {Button, Dialog, Icon, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import styles from '@/shared/ui/SaveDialogFooter/SaveDialogFooter.module.css';

export function SaveDialogFooter({
    saved,
    busy,
    disabled = false,
    saveLabel,
    cancelLabel,
    onSave,
    onClose,
}: {
    readonly saved: boolean;
    readonly busy: boolean;
    readonly disabled?: boolean;
    readonly saveLabel: string;
    readonly cancelLabel: string;
    readonly onSave: () => void;
    readonly onClose: () => void;
}) {
    const t = useTranslations();
    return (
        <Dialog.Footer
            textButtonCancel={cancelLabel}
            onClickButtonCancel={onClose}
            loading={busy}
            renderButtons={(_, cancel) => (
                <>
                    {cancel}
                    <Button
                        className={styles.apply}
                        type="submit"
                        view="action"
                        size="l"
                        loading={busy}
                        disabled={disabled}
                        onClick={onSave}
                        aria-label={saved ? t('saved') : saveLabel}
                    >
                        {saved && <Icon data={Check} size={18} />}
                        <Text role={saved ? 'status' : undefined}>{saved ? t('saved') : saveLabel}</Text>
                    </Button>
                </>
            )}
        />
    );
}
