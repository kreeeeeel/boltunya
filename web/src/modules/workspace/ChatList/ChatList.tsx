import {Box, Flex, Icon, Loader, Skeleton, Text, TextInput} from '@gravity-ui/uikit';
import {Magnifier, LogoTelegram} from '@gravity-ui/icons';
import {useLocale, useTranslations} from 'next-intl';
import {useMemo} from 'react';

import type {ChatListProps} from '@/modules/workspace/ChatList/ChatList.types';
import styles from '@/modules/workspace/ChatList/ChatList.module.css';
import {ChatListRow} from '@/modules/workspace/ChatList/ChatListRow';
import {useChatPages} from '@/modules/workspace/ChatList/useChatPages';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {SegmentedTabs} from '@/shared/ui/SegmentedTabs/SegmentedTabs';

export function ChatList({chats, selected, loading = false, onSelect}: ChatListProps) {
    const t = useTranslations();
    const locale = useLocale();
    const pages = useChatPages(chats, locale);
    const timeFormat = useMemo(
        () =>
            new Intl.DateTimeFormat(locale, {
                hour: '2-digit',
                minute: '2-digit',
                hour12: false,
            }),
        [locale],
    );
    const pending = loading || pages.changing;

    return (
        <Flex direction="column" className={styles.root}>
            <Flex alignItems="center" justifyContent="space-between" className={styles.heading}>
                <Flex alignItems="center" gap={2}>
                    <Text className={styles.headingTitle}>{t('chats')}</Text>
                    {loading ? (
                        <Skeleton width={20} height={14} />
                    ) : (
                        <Text className={styles.count}>{chats.length}</Text>
                    )}
                </Flex>
            </Flex>
            <Box className={styles.search}>
                {loading ? (
                    <Skeleton height={40} />
                ) : (
                    <TextInput
                        qa="chat-search"
                        className={styles.searchInput}
                        size="l"
                        view="clear"
                        value={pages.query.search}
                        onUpdate={(search) => pages.update({search})}
                        placeholder={t('search')}
                        hasClear
                        startContent={
                            <Box className={styles.searchIcon}>
                                <Icon data={Magnifier} size={18} />
                            </Box>
                        }
                        controlProps={{'aria-label': t('search')}}
                    />
                )}
            </Box>
            <Box className={styles.filters}>
                <SegmentedTabs
                    items={['all', 'withAi', 'unread'].map((value) => ({
                        value,
                        label: t(value),
                        disabled: loading,
                    }))}
                    value={pages.query.filter}
                    onUpdate={(filter) => pages.update({filter})}
                    label={t('chats')}
                />
            </Box>
            <ScrollArea
                className={styles.list}
                busy={pending || pages.loadingMore}
                qa="chat-list-scroll"
                viewportRef={pages.viewport}
                onScroll={pages.loadMore}
            >
                {pending ? (
                    <Box role="status" aria-label={t('loadingChats')} qa="chat-list-skeleton">
                        {Array.from({length: 8}, (_, index) => (
                            <Box className={styles.row} key={index} qa="chat-row-skeleton">
                                <Skeleton variant="circle" width={42} height={42} />
                                <Flex direction="column" className={styles.chatText}>
                                    <Skeleton width="68%" height={18} />
                                    <Skeleton width="90%" height={18} />
                                </Flex>
                                <Flex direction="column" alignItems="flex-end" className={styles.aside}>
                                    <Skeleton width={38} height={18} />
                                    <Skeleton width={20} height={20} />
                                </Flex>
                            </Box>
                        ))}
                    </Box>
                ) : (
                    pages.page.map((chat) => (
                        <ChatListRow
                            key={chat.id}
                            chat={chat}
                            selected={selected === chat.id}
                            timeFormat={timeFormat}
                            onSelect={onSelect}
                        />
                    ))
                )}
                {!pending && pages.empty && (
                    <Flex direction="column" alignItems="center" gap={3} className={styles.empty}>
                        <Icon data={LogoTelegram} size={30} />
                        <Text variant="subheader-2">{t(chats.length ? 'noResults' : 'emptyChats')}</Text>
                        {!chats.length && <Text color="secondary">{t('emptyChatsText')}</Text>}
                    </Flex>
                )}
                {!pending && pages.hasMore && (
                    <Flex
                        justifyContent="center"
                        alignItems="center"
                        className={styles.loadMore}
                        role="status"
                        aria-label={t('loadingChats')}
                        qa="chat-list-loader"
                    >
                        <Loader size="s" />
                    </Flex>
                )}
            </ScrollArea>
        </Flex>
    );
}
