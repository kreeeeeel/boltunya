import {Box} from '@gravity-ui/uikit';
import {useState} from 'react';
import type {Chat} from '@/shared/api/client';
import {usePreferences} from '@/shared/theme/Preferences';
import {fallbackWallpaper} from '@/modules/workspace/ChatWallpaper/fallbackWallpaper';
import styles from '@/modules/workspace/ChatWallpaper/ChatWallpaper.module.css';

export function ChatWallpaper({
    wallpaper,
    chatId = '',
}: {
    readonly wallpaper?: Chat['wallpaper'];
    readonly chatId?: string;
}) {
    const {theme} = usePreferences();
    const [failedUrl, setFailedUrl] = useState('');
    const available = !!wallpaper?.url && wallpaper.url !== failedUrl;
    const fallback = !wallpaper || (!!wallpaper.url && !available);
    const colors = fallback ? fallbackWallpaper(chatId, theme) : (wallpaper.colors ?? []);
    const fill =
        colors.length > 1
            ? `linear-gradient(${wallpaper?.rotation ?? 0}deg, ${colors.join(',')})`
            : undefined;
    const image = available ? `url("${wallpaper.url}")` : 'url("/assets/illustrations/chat-pattern.svg")';
    const photo = available && wallpaper?.kind === 'wallpaper';
    const plainFill = !fallback && wallpaper?.kind === 'fill';
    return (
        <Box
            qa="chat-wallpaper"
            className={styles.wallpaper}
            aria-hidden="true"
            style={{
                backgroundColor: colors[0],
                backgroundImage: plainFill ? fill : photo ? image : [image, fill].filter(Boolean).join(','),
                backgroundSize: photo || plainFill ? 'cover' : '240px 240px, cover',
                backgroundRepeat: photo || plainFill ? 'no-repeat' : 'repeat, no-repeat',
                filter: photo && wallpaper?.blurred ? 'blur(8px)' : undefined,
                opacity:
                    theme === 'dark' && wallpaper?.dimming && !fallback
                        ? 1 - wallpaper.dimming / 100
                        : undefined,
            }}
        >
            {available && (
                <Box
                    as="img"
                    src={wallpaper.url}
                    alt=""
                    className={styles.probe}
                    onError={() => setFailedUrl(wallpaper.url ?? '')}
                />
            )}
        </Box>
    );
}
