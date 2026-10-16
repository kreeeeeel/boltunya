import {Box, Button, Flex, Icon, Label, Skeleton, Text} from '@gravity-ui/uikit';
import {Plus, NodesRight, ChevronRight, Persons} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import type {AutomationFlow} from '@/modules/flows/model/flowTypes';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import styles from '@/modules/flows/FlowLibrary/FlowLibrary.module.css';

export function FlowLibrary({
    flows,
    onCreate,
    onOpen,
    loading = false,
}: {
    readonly flows: AutomationFlow[];
    readonly onCreate: () => void;
    readonly onOpen: (flow: AutomationFlow) => void;
    readonly loading?: boolean;
}) {
    const t = useTranslations();
    if (loading) {
        return (
            <ScrollArea className={styles.library}>
                <Box role="status" aria-label={t('loading')} qa="flows-skeleton">
                    <Flex className={styles.header} alignItems="center" justifyContent="space-between">
                        <Skeleton width={180} height={24} />
                        <Skeleton width={160} height={36} />
                    </Flex>
                    <Box className={styles.grid}>
                        {Array.from({length: 3}, (_, index) => (
                            <Box key={index} className={styles.card}>
                                <Flex justifyContent="space-between" alignItems="center">
                                    <Skeleton width={48} height={48} />
                                    <Skeleton width={80} height={20} />
                                </Flex>
                                <Skeleton width="70%" height={24} />
                                <Skeleton width="50%" height={20} />
                                <Skeleton height={20} />
                                <Box className={styles.footer}>
                                    <Skeleton width="60%" height={20} />
                                </Box>
                            </Box>
                        ))}
                    </Box>
                </Box>
            </ScrollArea>
        );
    }
    if (flows.length === 0) {
        return (
            <Flex
                direction="column"
                alignItems="center"
                justifyContent="center"
                className={styles.empty}
                qa="flow-empty"
            >
                <Box as="img" src="/assets/illustrations/activity.png" alt="" width={200} height={200} />
                <Text variant="header-1">{t('flows.empty')}</Text>
                <Text color="secondary">{t('flows.emptyHint')}</Text>
                <Button size="l" view="action" onClick={onCreate}>
                    <Icon data={Plus} size={18} />
                    {t('flows.create')}
                </Button>
            </Flex>
        );
    }
    return (
        <ScrollArea className={styles.library}>
            <Flex alignItems="center" justifyContent="space-between" gap={3} className={styles.header}>
                <Text variant="header-1">{t('flows.title')}</Text>
                <Button size="l" view="action" onClick={onCreate}>
                    <Icon data={Plus} size={18} />
                    {t('flows.create')}
                </Button>
            </Flex>
            <Box className={styles.grid}>
                {flows.map((flow) => (
                    <Box
                        as="button"
                        type="button"
                        className={styles.card}
                        key={flow.id}
                        onClick={() => onOpen(flow)}
                        qa="flow-card"
                    >
                        <Flex alignItems="center" justifyContent="space-between" gap={3}>
                            <Box className={styles.icon}>
                                <Icon data={NodesRight} size={24} />
                            </Box>
                            <Label theme={flow.enabled ? 'success' : 'normal'}>
                                {t(flow.enabled ? 'flows.active' : 'flows.inactive')}
                            </Label>
                        </Flex>
                        <Text variant="subheader-3" className={styles.name}>
                            {flow.name}
                        </Text>
                        <Flex gap={2} wrap className={styles.commands}>
                            {flow.nodes
                                .filter((node) => node.kind === 'command')
                                .map((node) => (
                                    <Label key={node.id} theme="info">
                                        {node.text || t('flows.kind.command')}
                                    </Label>
                                ))}
                        </Flex>
                        <Flex gap={2} wrap className={styles.steps}>
                            {Array.from(new Set(flow.nodes.map((node) => node.kind))).map((kind) => (
                                <Text key={kind}>{t('flows.kind.' + kind)}</Text>
                            ))}
                        </Flex>
                        <Flex
                            alignItems="center"
                            justifyContent="space-between"
                            gap={3}
                            className={styles.footer}
                        >
                            <Flex gap={2} alignItems="center">
                                <Icon data={Persons} size={16} />
                                <Text>
                                    {flow.chatIds.length
                                        ? t('flows.chatCount', {count: flow.chatIds.length})
                                        : t('flows.allChats')}
                                </Text>
                            </Flex>
                            <Icon data={ChevronRight} size={18} />
                        </Flex>
                    </Box>
                ))}
            </Box>
        </ScrollArea>
    );
}
