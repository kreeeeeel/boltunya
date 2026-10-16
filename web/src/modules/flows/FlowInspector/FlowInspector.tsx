import {Box, Button, Flex, Select, Text, TextArea, TextInput} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {FlowNode} from '@/modules/flows/model/flowTypes';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import styles from '@/modules/flows/FlowInspector/FlowInspector.module.css';

export function FlowInspector({
    node,
    onUpdate,
    onDelete,
}: {
    readonly node?: FlowNode;
    readonly onUpdate: (node: FlowNode) => void;
    readonly onDelete: () => void;
}) {
    const t = useTranslations();
    return (
        <ScrollArea className={styles.root}>
            <Flex direction="column" gap={4} className={styles.content}>
                <Text variant="subheader-2">
                    {node ? t('flows.kind.' + node.kind) : t('flows.selectBlock')}
                </Text>
                {node && (
                    <>
                        <Box>
                            <Text className={styles.label}>
                                {t(
                                    node.kind === 'agent'
                                        ? 'flows.agentPrompt'
                                        : node.kind === 'command'
                                          ? 'flows.command'
                                          : node.kind === 'condition'
                                            ? 'flows.condition'
                                            : 'flows.text',
                                )}
                            </Text>
                            <TextArea
                                value={node.text}
                                minRows={4}
                                maxRows={10}
                                onUpdate={(text) => onUpdate({...node, text})}
                                controlProps={{maxLength: 4000, 'aria-label': t('flows.text')}}
                            />
                        </Box>
                        {node.kind === 'command' && (
                            <Select
                                value={[node.match]}
                                options={['prefix', 'equals', 'contains'].map((value) => ({
                                    value,
                                    content: t('flows.match.' + value),
                                }))}
                                onUpdate={([match]) => onUpdate({...node, match: match as FlowNode['match']})}
                                width="max"
                                aria-label={t('flows.matchMode')}
                            />
                        )}
                        {node.kind === 'store' && (
                            <>
                                <Box>
                                    <Text className={styles.label}>{t('flows.key')}</Text>
                                    <TextInput
                                        value={node.key}
                                        onUpdate={(key) => onUpdate({...node, key})}
                                        controlProps={{'aria-label': t('flows.key'), maxLength: 80}}
                                    />
                                </Box>
                                <Select
                                    width="max"
                                    value={[node.operation]}
                                    options={['get', 'set', 'delete'].map((value) => ({
                                        value,
                                        content: t('flows.operation.' + value),
                                    }))}
                                    onUpdate={([operation]) =>
                                        onUpdate({...node, operation: operation as FlowNode['operation']})
                                    }
                                    aria-label={t('flows.operationLabel')}
                                />
                            </>
                        )}
                        {node.kind === 'condition' && <Text color="secondary">{t('flows.branchHint')}</Text>}
                        <Text className={styles.note}>{t('flows.variables')}</Text>
                        <Button view="flat-danger" onClick={onDelete} disabled={node.kind === 'command'}>
                            {t('flows.deleteBlock')}
                        </Button>
                    </>
                )}
                {!node && <Text color="secondary">{t('flows.connectHint')}</Text>}
            </Flex>
        </ScrollArea>
    );
}
