import {Box, Dialog, Flex, Label, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import type * as Contract from '@aigram/api-client';
import styles from '@/modules/diagnostics/ErrorDetailsDialog/ErrorDetailsDialog.module.css';
export function ErrorDetailsDialog({
    entry,
    onClose,
}: {
    readonly entry: Contract.DiagnosticEntry;
    readonly onClose: () => void;
}) {
    const t = useTranslations('diagnostics');
    const locale = useLocale();
    return (
        <Dialog
            open
            fullWidth
            maxWidth="s"
            contentOverflow="auto"
            onClose={onClose}
            aria-label={t('details')}
        >
            <Dialog.Header caption={t('details')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Flex gap={2} wrap>
                        <Label theme="danger">{t('sources.' + entry.source)}</Label>
                        <Label>{entry.code}</Label>
                    </Flex>
                    <Box className={styles.block}>
                        <Text className={styles.label}>{t('happened')}</Text>
                        <Text>{entry.message}</Text>
                    </Box>
                    <Box className={styles.block}>
                        <Text className={styles.label}>{t('solution')}</Text>
                        <Text>{entry.remediation}</Text>
                    </Box>
                    <Box className={styles.block}>
                        <Text className={styles.label}>{t('technical')}</Text>
                        <Flex direction="column" gap={2}>
                            <Text>
                                {t('lastOccurrence', {
                                    date: new Date(entry.date * 1000).toLocaleString(locale),
                                })}
                            </Text>
                            <Text>
                                {t('firstOccurrence', {
                                    date: new Date(entry.firstOccurredAt * 1000).toLocaleString(locale),
                                })}
                            </Text>
                            <Text>{t('occurrences', {count: entry.occurrences})}</Text>
                            {entry.exceptionType && (
                                <Text className={styles.code}>{entry.exceptionType}</Text>
                            )}
                            {Object.entries(entry.metadata).map(([key, value]) => (
                                <Flex key={key} gap={3} justifyContent="space-between">
                                    <Text color="secondary">{key}</Text>
                                    <Text className={styles.value}>{value}</Text>
                                </Flex>
                            ))}
                        </Flex>
                    </Box>
                    <Text color="secondary">{t('privacy')}</Text>
                </Flex>
            </Dialog.Body>
            <Dialog.Footer textButtonCancel={t('close')} onClickButtonCancel={onClose} />
        </Dialog>
    );
}
