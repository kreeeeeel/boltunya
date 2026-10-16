import {Box, Button, Flex, Link, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {Fragment, useState} from 'react';
import type {ReactNode} from 'react';

import type {MessageEntity} from '@/shared/api/client';
import {
    entityLink,
    inlineSegments,
    prepareEntities,
    structuralTypes,
} from '@/modules/workspace/RichText/formatMessage';
import styles from '@/modules/workspace/RichText/RichText.module.css';

export function RichText({
    text,
    entities,
    className,
}: {
    readonly text: string;
    readonly entities?: MessageEntity[];
    readonly className?: string;
}) {
    const t = useTranslations();
    const formatted = prepareEntities(text, entities);
    const [revealed, setRevealed] = useState<string[]>([]);
    const [expanded, setExpanded] = useState<string[]>([]);
    const [copied, setCopied] = useState<string[]>([]);
    const [error, setError] = useState('');

    function inline(start: number, end: number) {
        return inlineSegments(text, formatted, start, end).map((segment) => {
            const content = segment.entities.reduce<ReactNode>((children, entity) => {
                const href = ['url', 'texturl', 'email', 'phone', 'mention', 'mentionname'].includes(
                    entity.type,
                )
                    ? entityLink(entity, text)
                    : undefined;
                if (href) {
                    return (
                        <Link href={href} target="_blank" rel="noopener noreferrer" className={styles.link}>
                            {children}
                        </Link>
                    );
                }
                if (entity.type === 'code') {
                    return (
                        <Text as="code" className={styles.code}>
                            {children}
                        </Text>
                    );
                }
                const classes: Record<string, string> = {
                    bold: styles.bold,
                    italic: styles.italic,
                    underline: styles.underline,
                    strike: styles.strike,
                };
                return classes[entity.type] ? (
                    <Text className={[styles.format, classes[entity.type]].join(' ')}>{children}</Text>
                ) : (
                    children
                );
            }, segment.text);
            return <Fragment key={segment.offset}>{content}</Fragment>;
        });
    }

    function range(start: number, end: number, ignored: Set<MessageEntity> = new Set()): ReactNode {
        const block = formatted
            .filter(
                (entity) =>
                    structuralTypes.has(entity.type) &&
                    !ignored.has(entity) &&
                    entity.offset >= start &&
                    entity.offset + entity.length <= end,
            )
            .sort((left, right) => left.offset - right.offset || right.length - left.length)[0];
        if (!block) {
            return inline(start, end);
        }
        const stop = block.offset + block.length;
        const key = block.type + ':' + block.offset;
        const nextIgnored = new Set([...ignored, block]);
        const value = text.slice(block.offset, stop);
        let content: ReactNode;
        if (block.type === 'pre' || block.type === 'precode') {
            content = (
                <Box className={styles.codeBlock}>
                    <Flex justifyContent="space-between" alignItems="center" className={styles.codeHeader}>
                        <Text>{block.language || t('codeBlock')}</Text>
                        <Button
                            view="flat-secondary"
                            size="s"
                            onClick={async () => {
                                try {
                                    await navigator.clipboard.writeText(value);
                                    setCopied((previous) => [...previous, key]);
                                    setError('');
                                } catch {
                                    setError(t('copyError'));
                                }
                            }}
                        >
                            {t(copied.includes(key) ? 'copied' : 'copy')}
                        </Button>
                    </Flex>
                    <Box as="pre" className={styles.pre}>
                        {value}
                    </Box>
                </Box>
            );
        } else if (block.type === 'spoiler') {
            content = revealed.includes(key) ? (
                <Text className={styles.format}>{range(block.offset, stop, nextIgnored)}</Text>
            ) : (
                <Button
                    className={styles.spoiler}
                    view="flat"
                    aria-label={t('revealSpoiler')}
                    onClick={() => setRevealed((previous) => [...previous, key])}
                >
                    {value}
                </Button>
            );
        } else {
            content = (
                <Box as="blockquote" className={styles.quote}>
                    <Box
                        className={
                            block.type === 'expandablequote' && !expanded.includes(key)
                                ? styles.collapsed
                                : undefined
                        }
                    >
                        {range(block.offset, stop, nextIgnored)}
                    </Box>
                    {block.type === 'expandablequote' && !expanded.includes(key) && (
                        <Button
                            size="s"
                            view="flat-info"
                            onClick={() => setExpanded((previous) => [...previous, key])}
                        >
                            {t('expandQuote')}
                        </Button>
                    )}
                </Box>
            );
        }
        return (
            <Fragment key={key}>
                {inline(start, block.offset)}
                {content}
                {range(stop, end, ignored)}
            </Fragment>
        );
    }

    return (
        <Box className={[styles.root, className].filter(Boolean).join(' ')}>
            {range(0, text.length)}
            {error && <Text color="danger">{error}</Text>}
        </Box>
    );
}
