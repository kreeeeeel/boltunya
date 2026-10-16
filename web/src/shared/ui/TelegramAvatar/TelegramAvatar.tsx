import {Avatar, Box, Skeleton} from '@gravity-ui/uikit';
import {useState} from 'react';

import styles from '@/shared/ui/TelegramAvatar/TelegramAvatar.module.css';

export function TelegramAvatar({
    title,
    url,
    size = 'l',
    className,
    qa,
}: {
    readonly title: string;
    readonly url?: string;
    readonly size?: 's' | 'm' | 'l';
    readonly className?: string;
    readonly qa?: string;
}) {
    const [loaded, setLoaded] = useState('');
    const [failed, setFailed] = useState('');
    const available = !!url && failed !== url;
    return (
        <Box
            qa={qa}
            className={[styles.root, className].filter(Boolean).join(' ')}
            style={{width: {s: 28, m: 32, l: 42}[size], height: {s: 28, m: 32, l: 42}[size]}}
        >
            <Avatar text={title || 'Я'} size={size} className={styles.initials} />
            {available && (
                <>
                    {loaded !== url && <Skeleton variant="circle" className={styles.loading} />}
                    <Box
                        as="img"
                        src={url}
                        alt={title}
                        loading="lazy"
                        className={styles.image}
                        style={{opacity: loaded === url ? 1 : 0}}
                        onLoad={() => setLoaded(url)}
                        onError={() => setFailed(url)}
                    />
                </>
            )}
        </Box>
    );
}
