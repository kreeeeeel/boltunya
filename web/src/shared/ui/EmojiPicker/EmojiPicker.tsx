import {Box, Button, Popup} from '@gravity-ui/uikit';
import {useRef, useState} from 'react';
import {useTranslations} from 'next-intl';

import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import emojis from '@/shared/ui/EmojiPicker/emojis.json';
import styles from '@/shared/ui/EmojiPicker/EmojiPicker.module.css';

export function EmojiPicker({
    value,
    onUpdate,
}: {
    readonly value: string;
    readonly onUpdate: (emoji: string) => void;
}) {
    const t = useTranslations();
    const anchor = useRef<HTMLButtonElement>(null);
    const [open, setOpen] = useState(false);
    return (
        <>
            <Button
                ref={anchor}
                size="m"
                view="flat"
                onClick={() => setOpen(!open)}
                aria-label={t('chooseEmoji')}
                aria-haspopup="dialog"
                aria-expanded={open}
                className={styles.trigger}
            >
                {value}
            </Button>
            <Popup open={open} onClose={() => setOpen(false)} anchorRef={anchor} placement="bottom-start">
                <ScrollArea className={styles.popup}>
                    <Box className={styles.grid} role="dialog" aria-label={t('chooseEmoji')}>
                        {emojis.map((emoji) => (
                            <Button
                                key={emoji}
                                view="flat"
                                className={styles.emoji}
                                aria-label={emoji}
                                onClick={() => {
                                    onUpdate(emoji);
                                    setOpen(false);
                                }}
                            >
                                {emoji}
                            </Button>
                        ))}
                    </Box>
                </ScrollArea>
            </Popup>
        </>
    );
}
