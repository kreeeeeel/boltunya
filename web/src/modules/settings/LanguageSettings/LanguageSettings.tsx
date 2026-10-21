import {Box, Button, Flex, Text} from '@gravity-ui/uikit';

import {usePreferences} from '@/shared/theme/Preferences';
import styles from '@/modules/settings/LanguageSettings/LanguageSettings.module.css';

export function LanguageSettings() {
    const {locale, changeLocale} = usePreferences();
    return (
        <Flex direction="column" gap={3}>
            {(
                [
                    {id: 'ru', name: 'Русский'},
                    {id: 'en', name: 'English'},
                    {id: 'kk', name: 'Қазақша'},
                    {id: 'uk', name: 'Українська'},
                ] as const
            ).map((language) => (
                <Button
                    key={language.id}
                    size="xl"
                    view={locale === language.id ? 'outlined-info' : 'normal'}
                    className={styles.option}
                    selected={locale === language.id}
                    aria-pressed={locale === language.id}
                    onClick={() => changeLocale(language.id)}
                >
                    <Flex alignItems="center" gap={3}>
                        <Box
                            as="img"
                            src={'/assets/flags/' + language.id + '.svg'}
                            alt=""
                            width={30}
                            height={20}
                            className={styles.flag}
                        />
                        <Text>{language.name}</Text>
                    </Flex>
                </Button>
            ))}
        </Flex>
    );
}
