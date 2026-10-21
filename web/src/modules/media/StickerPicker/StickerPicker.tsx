import {Box, Button, Flex, Icon, Loader, Popup, Text, Tooltip} from '@gravity-ui/uikit';
import {Clock, Star} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import {useMediaMessages} from '@/modules/media/i18n/messages';
import {AnimatedSticker} from '@/modules/workspace/MessageMedia/AnimatedSticker';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import styles from '@/modules/media/StickerPicker/StickerPicker.module.css';
type Sticker = Contract.StickerItem;
type Catalog = Contract.StickerCatalog;
export function StickerPicker({
    anchor,
    onClose,
    onPick,
}: {
    readonly anchor: HTMLElement | null;
    readonly onClose: () => void;
    readonly onPick: (id: string) => Promise<void>;
}) {
    const t = useMediaMessages();
    const [catalog, setCatalog] = useState<Catalog>();
    const [set, setSet] = useState('recent');
    const [items, setItems] = useState<Sticker[]>();
    const [error, setError] = useState('');
    const [busy, setBusy] = useState('');
    const setRequest = useRef<AbortController | null>(null);
    useEffect(() => () => setRequest.current?.abort(), []);
    useEffect(() => {
        const controller = new AbortController();
        void api.media
            .stickers({
                signal: controller.signal,
            })
            .then((value) => {
                if (!controller.signal.aborted) {
                    setCatalog(value);
                }
            })
            .catch((cause) => {
                if (!controller.signal.aborted) {
                    setError(errorMessage(cause));
                }
            });
        return () => controller.abort();
    }, []);
    async function select(id: string) {
        setRequest.current?.abort();
        setRequest.current = null;
        setSet(id);
        setError('');
        setItems(undefined);
        if (id === 'recent' || id === 'favorites') {
            setBusy('');
            return;
        }
        const controller = new AbortController();
        setRequest.current = controller;
        setBusy(id);
        try {
            const items = await api.media.stickerSet(
                {
                    id,
                },
                {
                    signal: controller.signal,
                },
            );
            if (!controller.signal.aborted && setRequest.current === controller) {
                setItems(items);
            }
        } catch (cause) {
            if (!controller.signal.aborted) {
                setError(errorMessage(cause));
            }
        } finally {
            if (!controller.signal.aborted && setRequest.current === controller) {
                setBusy('');
            }
        }
    }
    async function pick(id: string) {
        if (busy) {
            return;
        }
        setBusy('send');
        setError('');
        try {
            await onPick(id);
            onClose();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    const stickers = set === 'recent' ? catalog?.recent : set === 'favorites' ? catalog?.favorites : items;
    return (
        <Popup
            open
            anchorElement={anchor}
            placement={['top-end', 'top-start']}
            offset={12}
            onOpenChange={(open) => {
                if (!open && busy !== 'send') {
                    onClose();
                }
            }}
            className={styles.popup}
            aria-label={t.stickers}
        >
            <Flex direction="column" className={styles.root} qa="sticker-picker">
                <Flex className={styles.tabs} gap={1} role="tablist" aria-label={t.stickers}>
                    {[
                        {id: 'recent', title: t.recent},
                        {id: 'favorites', title: t.favorites},
                        ...(catalog?.sets ?? []),
                    ].map((item) => (
                        <Tooltip key={item.id} content={item.title}>
                            <Button
                                key={item.id}
                                view="flat-secondary"
                                selected={set === item.id}
                                role="tab"
                                aria-selected={set === item.id}
                                aria-label={item.title}
                                className={styles.pack}
                                onClick={() => void select(item.id)}
                                disabled={busy === 'send'}
                            >
                                {item.id === 'recent' ? (
                                    <Icon data={Clock} size={20} />
                                ) : item.id === 'favorites' ? (
                                    <Icon data={Star} size={20} />
                                ) : (
                                    <Text ellipsis>{item.title}</Text>
                                )}
                            </Button>
                        </Tooltip>
                    ))}
                </Flex>
                <ScrollArea className={styles.content}>
                    {stickers ? (
                        stickers.length ? (
                            <Box className={styles.grid}>
                                {stickers.map((sticker) => (
                                    <Button
                                        key={sticker.id}
                                        className={styles.sticker}
                                        view="flat"
                                        disabled={!!busy}
                                        aria-label={sticker.emoji || t.stickers}
                                        onClick={() => void pick(sticker.id)}
                                    >
                                        {sticker.media.mimeType === 'application/x-tgsticker' ? (
                                            <AnimatedSticker fileId={sticker.media.fileId} />
                                        ) : sticker.media.mimeType.startsWith('video/') ? (
                                            <Box
                                                as="video"
                                                src={`/api/media/${sticker.media.fileId}`}
                                                autoPlay
                                                muted
                                                loop
                                                playsInline
                                            />
                                        ) : (
                                            <Box
                                                as="img"
                                                src={`/api/media/${sticker.media.fileId}`}
                                                alt={sticker.emoji}
                                                loading="lazy"
                                            />
                                        )}
                                    </Button>
                                ))}
                            </Box>
                        ) : (
                            <Flex className={styles.empty} alignItems="center" justifyContent="center">
                                <Text color="secondary">{t.emptyStickers}</Text>
                            </Flex>
                        )
                    ) : (
                        !error && (
                            <Flex
                                className={styles.empty}
                                alignItems="center"
                                justifyContent="center"
                                role="status"
                            >
                                <Loader size="m" />
                            </Flex>
                        )
                    )}
                    {error && (
                        <Text className={styles.error} role="alert" color="danger">
                            {error}
                        </Text>
                    )}
                </ScrollArea>
            </Flex>
        </Popup>
    );
}
