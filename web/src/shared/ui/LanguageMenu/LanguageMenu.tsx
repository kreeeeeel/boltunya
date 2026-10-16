import {Box, Button, DropdownMenu, Icon} from '@gravity-ui/uikit';
import {Globe} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {usePreferences} from '@/shared/theme/Preferences';

export function useLanguageItems() {
    const {locale, changeLocale} = usePreferences();
    return [
        {id: 'ru', title: 'Русский'},
        {id: 'en', title: 'English'},
        {id: 'kk', title: 'Қазақша'},
        {id: 'uk', title: 'Українська'},
    ].map(({id, title}) => ({
        text: title,
        iconStart: (
            <Box
                as="img"
                src={`/assets/flags/${id}.svg`}
                alt=""
                width={24}
                height={16}
                style={{borderRadius: 3, objectFit: 'cover'}}
            />
        ),
        action: () => changeLocale(id as typeof locale),
        selected: locale === id,
    }));
}

export function LanguageMenu() {
    const t = useTranslations();
    const items = useLanguageItems();
    return (
        <DropdownMenu
            size="l"
            items={items}
            renderSwitcher={(props) => (
                <Button {...props} view="flat-secondary" aria-label={t('language')}>
                    <Icon data={Globe} size={20} />
                </Button>
            )}
        />
    );
}
