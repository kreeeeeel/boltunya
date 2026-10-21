import {Box, Flex, RadioGroup, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';

import {usePreferences} from '@/shared/theme/Preferences';
import styles from '@/modules/settings/LanguageSettings/LanguageSettings.module.css';

export function LanguageSettings() {
    const {locale, changeLocale} = usePreferences();
    const t = useTranslations();
    return (
        <RadioGroup
            value={locale}
            onUpdate={(next) => changeLocale(next as typeof locale)}
            direction="vertical"
            size="l"
            className={styles.options}
            optionClassName={styles.option}
            aria-label={t('language')}
            options={(
                [
                    {id: 'ru', name: 'Русский'},
                    {id: 'en', name: 'English'},
                    {id: 'kk', name: 'Қазақша'},
                    {id: 'uk', name: 'Українська'},
                ] as const
            ).map((language) => ({
                value: language.id,
                content: (
                    <Flex alignItems="center" gap={3} className={styles.content}>
                        <Box
                            as="img"
                            src={'/assets/flags/' + language.id + '.svg'}
                            alt=""
                            width={30}
                            height={20}
                            className={styles.flag}
                        />
                        <Text className={styles.name}>{language.name}</Text>
                    </Flex>
                ),
            }))}
        />
    );
}
