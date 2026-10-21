import {ArrowRotateRight, ArrowUpFromLine, FolderOpen} from '@gravity-ui/icons';
import {Button, Flex, Icon, Tooltip} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

export function SpeechToolbar({
    onRefresh,
    onBrowse,
    onImport,
    importExpanded = false,
    importControls,
    browseHint,
    busy = false,
}: {
    readonly onRefresh: () => void;
    readonly onBrowse?: () => Promise<void>;
    readonly onImport?: () => void;
    readonly importExpanded?: boolean;
    readonly importControls?: string;
    readonly browseHint?: string;
    readonly busy?: boolean;
}) {
    const t = useTranslations('voice');
    return (
        <Flex gap={2} wrap="wrap" qa="speech-toolbar">
            <Button size="l" view="flat-secondary" disabled={busy} onClick={onRefresh}>
                <Icon data={ArrowRotateRight} size={20} />
                {t('refreshList')}
            </Button>
            <Tooltip content={onBrowse ? t('openFolder') : browseHint || t('desktopOnly')}>
                <Button
                    size="l"
                    view="flat-secondary"
                    disabled={busy || !onBrowse}
                    onClick={() => void onBrowse?.()}
                >
                    <Icon data={FolderOpen} size={20} />
                    {t('openFolder')}
                </Button>
            </Tooltip>
            {onImport && (
                <Button
                    size="l"
                    view="flat-secondary"
                    disabled={busy}
                    onClick={onImport}
                    aria-expanded={importExpanded}
                    aria-controls={importControls}
                >
                    <Icon data={ArrowUpFromLine} size={20} />
                    {t('importModel')}
                </Button>
            )}
        </Flex>
    );
}
