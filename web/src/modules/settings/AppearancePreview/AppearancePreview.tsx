import {Box, Flex, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {Appearance} from '@/shared/theme/appearance';
import {appearancePalette} from '@/modules/settings/model/appearancePalette';
import styles from '@/modules/settings/AppearancePreview/AppearancePreview.module.css';

export function AppearancePreview({
    value,
    theme,
}: {
    readonly value: Appearance;
    readonly theme: 'light' | 'dark';
}) {
    const t = useTranslations('appearance');
    const colors = appearancePalette(value, theme);
    return (
        <Box
            className={styles.root}
            qa="appearance-live-preview"
            style={{background: colors.background, color: colors.text, borderColor: colors.border}}
        >
            <Flex alignItems="center" gap={3} className={styles.heading} style={{background: colors.panel}}>
                <Flex
                    className={styles.avatar}
                    alignItems="center"
                    justifyContent="center"
                    style={{background: colors.accent}}
                >
                    A
                </Flex>
                <Flex direction="column" gap={1}>
                    <Text className={styles.name}>{t('exampleName')}</Text>
                    <Text style={{color: colors.muted}}>{t('livePreview')}</Text>
                </Flex>
            </Flex>
            <Flex direction="column" gap={3} className={styles.messages}>
                <Box className={styles.incoming} style={{background: colors.incoming}}>
                    {t('exampleIncoming')}
                </Box>
                <Box className={styles.outgoing} style={{background: colors.outgoing}}>
                    {t('exampleOutgoing')}
                </Box>
            </Flex>
            <Box className={styles.input} style={{background: colors.panel, color: colors.muted}}>
                {t('examplePlaceholder')}
            </Box>
        </Box>
    );
}
