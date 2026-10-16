import {Box, Dialog, Flex, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import styles from '@/modules/flows/FlowTestDialog/FlowTestDialog.module.css';

export function FlowTestDialog({
    text,
    onUpdate,
    result,
    error,
    busy,
    onRun,
    onClose,
}: {
    readonly text: string;
    readonly onUpdate: (text: string) => void;
    readonly result: string;
    readonly error: string;
    readonly busy: boolean;
    readonly onRun: () => void;
    readonly onClose: () => void;
}) {
    const t = useTranslations();
    return (
        <Dialog
            open
            maxWidth="s"
            fullWidth
            contentOverflow="auto"
            onClose={onClose}
            aria-label={t('flows.testTitle')}
        >
            <Dialog.Header caption={t('flows.testTitle')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Text color="secondary">{t('flows.testHint')}</Text>
                    <Box>
                        <Text as="label" htmlFor="flow-test-input" className={styles.label}>
                            {t('flows.testInput')}
                        </Text>
                        <TextArea
                            id="flow-test-input"
                            value={text}
                            onUpdate={onUpdate}
                            minRows={3}
                            maxRows={7}
                            controlProps={{maxLength: 4096}}
                        />
                    </Box>
                    {result && (
                        <Box className={styles.result} qa="flow-test-result">
                            <Text>{result}</Text>
                        </Box>
                    )}
                    {error && (
                        <Text color="danger" role="alert">
                            {error}
                        </Text>
                    )}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={t('flows.test')}
                textButtonCancel={t('close')}
                onClickButtonApply={onRun}
                onClickButtonCancel={onClose}
                loading={busy}
                propsButtonApply={{disabled: !text.trim()}}
            />
        </Dialog>
    );
}
