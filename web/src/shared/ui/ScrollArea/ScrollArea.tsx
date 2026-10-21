'use client';

import {Box} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useCallback, useEffect, useId, useImperativeHandle, useRef, useState} from 'react';
import type {ReactNode, RefObject} from 'react';

import styles from '@/shared/ui/ScrollArea/ScrollArea.module.css';

interface ScrollAreaProps {
    readonly children: ReactNode;
    readonly className?: string;
    readonly qa?: string;
    readonly viewportRef?: RefObject<HTMLDivElement | null>;
    readonly onScroll?: () => void;
    readonly onResize?: () => void;
    readonly busy?: boolean;
    readonly fillViewport?: boolean;
}

export function ScrollArea({
    children,
    className,
    qa,
    viewportRef,
    onScroll,
    onResize,
    busy,
    fillViewport = false,
}: ScrollAreaProps) {
    const t = useTranslations();
    const id = useId();
    const viewport = useRef<HTMLDivElement>(null);
    useImperativeHandle<HTMLDivElement | null, HTMLDivElement | null>(viewportRef, () => viewport.current);
    const content = useRef<HTMLDivElement>(null);
    const track = useRef<HTMLDivElement>(null);
    const thumb = useRef<HTMLDivElement>(null);
    const drag = useRef<{y: number; top: number} | null>(null);
    const [metrics, setMetrics] = useState({height: 0, total: 0, top: 0, offset: 0});
    const measure = useCallback(() => {
        const element = viewport.current;
        if (element) {
            setMetrics({
                height: element.clientHeight,
                total: element.scrollHeight,
                top: element.scrollTop,
                offset: element.offsetTop,
            });
        }
    }, []);

    useEffect(() => {
        const observer = new ResizeObserver(() => {
            onResize?.();
            measure();
        });
        if (viewport.current) {
            observer.observe(viewport.current);
        }
        if (content.current) {
            observer.observe(content.current);
        }
        return () => observer.disconnect();
    }, [measure, onResize]);

    const overflow = metrics.total - metrics.height;
    const thumbHeight = Math.min(metrics.height, Math.max(32, metrics.height ** 2 / (metrics.total || 1)));
    const travel = metrics.height - thumbHeight;
    const ratio = overflow > 0 ? metrics.top / overflow : 0;

    return (
        <Box
            className={[styles.root, fillViewport ? styles.fullViewport : undefined, className]
                .filter(Boolean)
                .join(' ')}
        >
            <Box
                id={id}
                ref={viewport}
                qa={qa}
                className={styles.viewport}
                aria-busy={busy}
                onScroll={() => {
                    measure();
                    onScroll?.();
                }}
            >
                <Box ref={content} className={styles.content}>
                    {children}
                </Box>
            </Box>
            {overflow > 1 && (
                <Box
                    ref={track}
                    className={styles.track}
                    style={{top: metrics.offset, height: metrics.height, bottom: 'auto'}}
                    onPointerDown={(event) => {
                        if (event.target === event.currentTarget && viewport.current) {
                            const y = event.clientY - (track.current?.getBoundingClientRect().top ?? 0);
                            viewport.current.scrollTop = ((y - thumbHeight / 2) / travel) * overflow;
                        }
                    }}
                >
                    <Box
                        ref={thumb}
                        role="scrollbar"
                        aria-label={t('scroll')}
                        aria-controls={id}
                        aria-orientation="vertical"
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={Math.round(ratio * 100)}
                        tabIndex={0}
                        className={styles.thumb}
                        style={{height: thumbHeight, transform: `translateY(${ratio * travel}px)`}}
                        onPointerDown={(event) => {
                            event.preventDefault();
                            thumb.current?.setPointerCapture(event.pointerId);
                            drag.current = {y: event.clientY, top: metrics.top};
                        }}
                        onPointerMove={(event) => {
                            if (drag.current && viewport.current && travel > 0) {
                                viewport.current.scrollTop =
                                    drag.current.top + ((event.clientY - drag.current.y) / travel) * overflow;
                            }
                        }}
                        onPointerUp={(event) => thumb.current?.releasePointerCapture(event.pointerId)}
                        onLostPointerCapture={() => {
                            drag.current = null;
                        }}
                        onKeyDown={(event) => {
                            const element = viewport.current;
                            if (!element) {
                                return;
                            }
                            const destinations: Record<string, number> = {
                                ArrowDown: element.scrollTop + 40,
                                ArrowUp: element.scrollTop - 40,
                                PageDown: element.scrollTop + element.clientHeight,
                                PageUp: element.scrollTop - element.clientHeight,
                                Home: 0,
                                End: element.scrollHeight - element.clientHeight,
                            };
                            if (event.key in destinations) {
                                event.preventDefault();
                                element.scrollTop = destinations[event.key];
                                measure();
                            }
                        }}
                    />
                </Box>
            )}
        </Box>
    );
}
