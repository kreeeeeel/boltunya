'use client';

import {useLayoutEffect, useRef, useState} from 'react';
import {Box, Tab, TabList, Text} from '@gravity-ui/uikit';
import type {SegmentedTabsProps} from '@/shared/ui/SegmentedTabs/SegmentedTabs.types';
import styles from '@/shared/ui/SegmentedTabs/SegmentedTabs.module.css';

export function SegmentedTabs({items, value, onUpdate, label}: SegmentedTabsProps) {
    const root = useRef<HTMLDivElement>(null);
    const list = useRef<HTMLDivElement>(null);
    const [indicator, setIndicator] = useState<{
        left: number;
        top: number;
        width: number;
        height: number;
    }>();

    useLayoutEffect(() => {
        const container = root.current;
        const tabs = list.current;

        if (!container || !tabs) {
            return;
        }

        function measure() {
            const active = tabs?.querySelector<HTMLElement>('[aria-selected="true"]');

            if (!active || !container) {
                return;
            }

            const bounds = active.getBoundingClientRect();
            const parent = container.getBoundingClientRect();
            const next = {
                left: bounds.left - parent.left + container.scrollLeft,
                top: bounds.top - parent.top,
                width: bounds.width,
                height: bounds.height,
            };

            setIndicator((previous) =>
                previous?.left === next.left &&
                previous.top === next.top &&
                previous.width === next.width &&
                previous.height === next.height
                    ? previous
                    : next,
            );
        }

        const frame = requestAnimationFrame(measure);
        const observer = new ResizeObserver(measure);

        observer.observe(container);
        tabs.querySelectorAll('[role="tab"]').forEach((tab) => observer.observe(tab));

        return () => {
            cancelAnimationFrame(frame);
            observer.disconnect();
        };
    }, [items, value]);

    return (
        <Box ref={root} className={styles.root} data-ready={Boolean(indicator)}>
            {indicator && (
                <Box
                    aria-hidden="true"
                    data-testid="tab-indicator"
                    className={styles.indicator}
                    style={{
                        width: indicator.width,
                        height: indicator.height,
                        transform: `translate(${indicator.left}px, ${indicator.top}px)`,
                    }}
                />
            )}
            <TabList
                ref={list}
                value={value}
                onUpdate={onUpdate}
                aria-label={label}
                className={styles.list}
                size="l"
                contentOverflow="wrap"
            >
                {items.map((item) => (
                    <Tab
                        key={item.value}
                        value={item.value}
                        icon={item.icon && <Box className={styles.icon}>{item.icon}</Box>}
                        disabled={item.disabled}
                        className={styles.tab}
                    >
                        <Text as="span" variant="body-2" className={styles.label}>
                            {item.label}
                        </Text>
                    </Tab>
                ))}
            </TabList>
        </Box>
    );
}
