import {Box, Button, Flex, Icon, Label, Switch, Text} from '@gravity-ui/uikit';
import {BookOpen, Sliders, Thunderbolt, Xmark} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';

import type {InspectorProps} from '@/modules/workspace/Inspector/Inspector.types';
import styles from '@/modules/workspace/Inspector/Inspector.module.css';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {communicationStyles} from '@/modules/workspace/model/communicationStyles';
import {ChatHistoryActions} from '@/modules/workspace/ChatHistoryActions';

export function Inspector({
    chat,
    customStyles,
    onConfigure,
    onEnable,
    onClose,
    onCollectContext,
    onDeleted,
}: InspectorProps) {
    const t = useTranslations();
    const preset = customStyles.find((style) => style.id === chat.rule.styleId);
    const style =
        communicationStyles.find((item) => item.value === chat.rule.style) ?? communicationStyles[0];
    return (
        <Flex direction="column" className={styles.root} qa="conversation-settings">
            <Flex alignItems="center" justifyContent="space-between" className={styles.header}>
                <Text variant="subheader-2">{t('conversationSettings')}</Text>
                <Button view="flat-secondary" onClick={onClose} aria-label={t('close')}>
                    <Icon data={Xmark} size={17} />
                </Button>
            </Flex>
            <ScrollArea className={styles.content}>
                <Flex direction="column" gap={5}>
                    <Flex justifyContent="space-between" alignItems="center">
                        <Text className={styles.strong}>{t('enableAi')}</Text>
                        <Switch
                            checked={chat.rule.enabled}
                            onUpdate={onEnable}
                            controlProps={{'aria-label': t('enableAi')}}
                        />
                    </Flex>
                    <Box className={styles.line} />
                    <Box>
                        <Text className={styles.sectionLabel}>{t('style')}</Text>
                        <Label size="m" theme="info" icon={<Text>{preset?.emoji ?? style.emoji}</Text>}>
                            {preset?.name ?? t(style.value)}
                        </Label>
                    </Box>
                    <Box>
                        <Text className={styles.sectionLabel}>{t('model')}</Text>
                        <Flex gap={2} alignItems="center">
                            <Icon data={Thunderbolt} size={17} />
                            <Text className={styles.strong}>
                                {chat.rule.model
                                    .replace('qwen3:', 'Qwen 3 · ')
                                    .replace('qwen2.5:', 'Qwen 2.5 · ')
                                    .replace('gemma3:', 'Gemma 3 · ')}
                            </Text>
                        </Flex>
                        <Text className={styles.caption}>{t('statusOnline')}</Text>
                    </Box>
                    <Flex direction="column" gap={2} className={styles.context}>
                        <Icon data={BookOpen} size={21} />
                        <Text className={styles.strong}>{t('context.heading')}</Text>
                        <Text className={styles.caption}>
                            {t('contextHint', {count: chat.rule.contextMessages})}
                        </Text>
                        <Flex gap={1} className={styles.segments}>
                            {Array.from({length: 12}, (_, index) => (
                                <Box
                                    key={index}
                                    className={
                                        index < chat.rule.contextMessages / 5
                                            ? styles.segmentActive
                                            : styles.segment
                                    }
                                />
                            ))}
                        </Flex>
                    </Flex>
                    <ChatHistoryActions
                        key={chat.id}
                        chatId={chat.id}
                        onCollectContext={onCollectContext}
                        onDeleted={onDeleted}
                    />
                    <Button view="outlined-info" size="l" onClick={onConfigure}>
                        <Icon data={Sliders} size={16} />
                        {t('configure')}
                    </Button>
                </Flex>
            </ScrollArea>
        </Flex>
    );
}
