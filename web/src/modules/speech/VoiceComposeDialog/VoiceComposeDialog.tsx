import {Check, Microphone, PaperPlane, Xmark} from '@gravity-ui/icons';
import {Box, Button, Dialog, Flex, Icon, Loader, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useVoiceComposition} from '@/modules/speech/VoiceComposeDialog/useVoiceComposition';
import {VoiceComposeFields} from '@/modules/speech/VoiceComposeDialog/components/VoiceComposeFields/VoiceComposeFields';
import type {VoiceComposeDialogProps} from '@/modules/speech/VoiceComposeDialog/VoiceComposeDialog.types';
import {SpeechPlayer} from '@/modules/speech/VoiceDraftPreview/SpeechPlayer';
import styles from '@/modules/speech/VoiceComposeDialog/VoiceComposeDialog.module.css';

export function VoiceComposeDialog(props: VoiceComposeDialogProps) {
    const t = useTranslations('voice');
    const common = useTranslations();
    const composition = useVoiceComposition(props);
    return (
        <Dialog
            open
            contentOverflow="auto"
            hasCloseButton={false}
            className={styles.dialog}
            modalClassName={styles.modal}
            aria-label={t('manualTitle')}
            onClose={composition.close}
            disableOutsideClick={composition.sending}
        >
            <Dialog.Header caption={t('manualTitle')} />
            <Dialog.Body className={styles.body}>
                <Flex direction="column" gap={4}>
                    <Text color="secondary">{t('manualHint')}</Text>
                    <VoiceComposeFields composition={composition} />
                    {composition.tooLong && (
                        <Text color="danger" role="alert">
                            {t('transcriptTooLong')}
                        </Text>
                    )}
                    {composition.generating && (
                        <Flex gap={2} alignItems="center" role="status">
                            <Loader size="s" />
                            <Text>
                                {t(composition.status === 'queued' ? 'previewQueued' : 'previewRunning')}
                            </Text>
                        </Flex>
                    )}
                    {composition.status === 'cancelled' && (
                        <Text color="secondary" role="status">
                            {t('previewCancelled')}
                        </Text>
                    )}
                    {!!composition.voice && !composition.generating && (
                        <SpeechPlayer key={composition.voice.id} voice={composition.voice} />
                    )}
                    {composition.changedAudio && (
                        <Text color="secondary" role="status">
                            {t('manualChanged')}
                        </Text>
                    )}
                    {composition.error && (
                        <Text color="danger" role="alert">
                            {composition.error}
                        </Text>
                    )}
                    {composition.warning && (
                        <Flex direction="column" gap={2}>
                            <Text color="warning" role="alert">
                                {composition.warning}
                            </Text>
                            <Box>
                                <Button
                                    size="l"
                                    view="flat-secondary"
                                    loading={composition.refreshing}
                                    onClick={() => void composition.refresh()}
                                >
                                    {t('manualRefresh')}
                                </Button>
                            </Box>
                        </Flex>
                    )}
                    {composition.delivered && (
                        <Flex alignItems="center" gap={2} role="status">
                            <Icon data={Check} size={18} />
                            <Text>{t('manualSent')}</Text>
                        </Flex>
                    )}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonCancel={common('close')}
                onClickButtonCancel={composition.close}
                propsButtonCancel={{disabled: composition.sending}}
                renderButtons={(_, closeButton) => (
                    <Flex gap={2} wrap="wrap" className={styles.actions}>
                        {closeButton}
                        {composition.generating ? (
                            <Button
                                size="l"
                                view="flat-secondary"
                                disabled={!composition.hasJob || composition.cancelling}
                                onClick={() => void composition.cancel()}
                            >
                                <Icon data={Xmark} size={18} />
                                {t('cancel')}
                            </Button>
                        ) : (
                            <Button
                                size="l"
                                view="flat-secondary"
                                disabled={!composition.canGenerate}
                                onClick={() => void composition.generate()}
                            >
                                <Icon data={Microphone} size={18} />
                                {t('previewCheck')}
                            </Button>
                        )}
                        <Button
                            className={styles.primary}
                            size="l"
                            view="action"
                            loading={composition.sending || composition.autoSending}
                            disabled={!composition.canSend}
                            onClick={() => void composition.send()}
                            aria-label={t(composition.delivered ? 'manualSent' : 'manualSpeakAndSend')}
                        >
                            <Icon data={composition.delivered ? Check : PaperPlane} size={18} />
                            {t(composition.delivered ? 'manualSent' : 'manualSpeakAndSend')}
                        </Button>
                    </Flex>
                )}
            />
        </Dialog>
    );
}
