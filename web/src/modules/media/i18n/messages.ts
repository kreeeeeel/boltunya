import {useLocale} from 'next-intl';
import ru from '@/modules/media/i18n/ru.json';
import en from '@/modules/media/i18n/en.json';
import kk from '@/modules/media/i18n/kk.json';
import uk from '@/modules/media/i18n/uk.json';

const messages = {ru, en, kk, uk};

export function useMediaMessages(): typeof ru {
    const locale = useLocale();
    return messages[locale as keyof typeof messages] ?? en;
}
