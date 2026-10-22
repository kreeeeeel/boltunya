import {Button, Dialog, Icon} from '@gravity-ui/uikit';
import {Xmark} from '@gravity-ui/icons';
import type {SpeechAsset} from '@aigram/api-client';
import {useEffect, useRef} from 'react';
import {useTranslations} from 'next-intl';
import {SpeechPreview} from '@/modules/speech/SpeechPreview/SpeechPreview';
import type {SpeechPreviewHandle} from '@/modules/speech/SpeechPreview/SpeechPreview';
import {speechReady} from '@/modules/speech/model/speechReady';
import styles from '@/modules/speech/SpeechLibraryPreview/SpeechLibraryPreview.module.css';

export function SpeechLibraryPreview({
    asset,
    assets,
    speed,
    onClose,
}: {
    readonly asset: SpeechAsset;
    readonly assets: SpeechAsset[];
    readonly speed: number;
    readonly onClose: () => void;
}) {
    const t = useTranslations('voice');
    const common = useTranslations();
    const preview = useRef<SpeechPreviewHandle | null>(null);

    useEffect(() => {
        const frame = requestAnimationFrame(() => preview.current?.check(asset.id));
        return () => cancelAnimationFrame(frame);
    }, [asset.id]);

    return (
        <Dialog
            open
            hasCloseButton={false}
            onClose={onClose}
            aria-label={t('previewTitle')}
            className={styles.dialog}
            contentOverflow="auto"
        >
            <Dialog.Header caption={asset.title} />
            <Button
                className={styles.close}
                view="flat-secondary"
                size="l"
                onClick={onClose}
                aria-label={common('close')}
            >
                <Icon data={Xmark} size={20} />
            </Button>
            <Dialog.Body className={styles.body}>
                <SpeechPreview
                    ref={preview}
                    modelId={asset.id}
                    assets={assets}
                    speed={speed}
                    ready={speechReady(assets, asset.id)}
                />
            </Dialog.Body>
        </Dialog>
    );
}
