import {Box} from '@gravity-ui/uikit';
import {useState} from 'react';
import type {Chat} from '@/shared/api/client';
import {usePreferences} from '@/shared/theme/Preferences';
import styles from '@/modules/workspace/ChatWallpaper/ChatWallpaper.module.css';

export function ChatWallpaper({wallpaper}: {readonly wallpaper?: Chat['wallpaper']}) {
    const {theme} = usePreferences();
    const [failedUrl, setFailedUrl] = useState('');
    const available = !!wallpaper?.url && wallpaper.url !== failedUrl;
    const fallback = !!wallpaper?.url && !available;
    const colors = fallback ? [] : (wallpaper?.colors ?? []);
    const fill =
        colors.length > 1
            ? `linear-gradient(${wallpaper?.rotation ?? 0}deg, ${colors.join(',')})`
            : undefined;
    const image = available ? `url("${wallpaper.url}")` : undefined;
    const photo = available && wallpaper?.kind === 'wallpaper';
    const pattern = available && wallpaper?.kind === 'pattern';
    return (
        <Box
            qa="chat-wallpaper"
            className={styles.wallpaper}
            aria-hidden="true"
            style={{
                backgroundColor: colors[0] ?? 'var(--tiho-chat)',
                backgroundImage: photo ? image : pattern ? [image, fill].filter(Boolean).join(',') : fill,
                backgroundSize: pattern ? '240px 240px, cover' : 'cover',
                backgroundRepeat: pattern ? 'repeat, no-repeat' : 'no-repeat',
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
