import {Box} from '@gravity-ui/uikit';
import type {Chat} from '@/shared/api/client';
import styles from '@/modules/workspace/ChatWallpaper/ChatWallpaper.module.css';

export function ChatWallpaper({wallpaper}: {readonly wallpaper?: Chat['wallpaper']}) {
    const colors = wallpaper?.colors ?? [];
    const fill =
        colors.length > 1
            ? `linear-gradient(${wallpaper?.rotation ?? 0}deg, ${colors.join(',')})`
            : undefined;
    const image = wallpaper?.url
        ? `url("${wallpaper.url}")`
        : 'url("/assets/illustrations/chat-pattern.svg")';
    const photo = wallpaper?.kind === 'wallpaper';
    const plainFill = wallpaper?.kind === 'fill';
    return (
        <Box
            className={styles.wallpaper}
            aria-hidden="true"
            style={{
                backgroundColor: colors[0],
                backgroundImage: plainFill ? fill : photo ? image : [image, fill].filter(Boolean).join(','),
                backgroundSize: photo ? 'cover' : '240px 240px, cover',
                backgroundRepeat: photo ? 'no-repeat' : 'repeat',
                filter: wallpaper?.blurred ? 'blur(8px)' : undefined,
                opacity: photo && wallpaper?.dimming ? 1 - wallpaper.dimming / 100 : undefined,
            }}
        />
    );
}
