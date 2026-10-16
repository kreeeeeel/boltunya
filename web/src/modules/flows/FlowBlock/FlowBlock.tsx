import {Box, Flex, Text} from '@gravity-ui/uikit';
import {Handle, Position, type NodeProps} from '@xyflow/react';
import {useTranslations} from 'next-intl';
import type {FlowCanvasNode} from '@/modules/flows/model/flowTypes';
import styles from '@/modules/flows/FlowBlock/FlowBlock.module.css';

const symbols = {command: '⌘', agent: '✦', reply: '↗', store: '▤', condition: '⑂'};

export function FlowBlock({data, selected}: NodeProps<FlowCanvasNode>) {
    const t = useTranslations();
    const node = data.config;
    return (
        <Box
            qa="flow-block"
            className={[styles.block, styles[node.kind], selected ? styles.selected : ''].join(' ')}
        >
            {node.kind !== 'command' && <Handle type="target" position={Position.Left} />}
            <Flex alignItems="center" gap={3}>
                <Box className={styles.symbol}>{symbols[node.kind]}</Box>
                <Text variant="subheader-2">{t('flows.kind.' + node.kind)}</Text>
            </Flex>
            <Text className={styles.preview}>{node.text || node.key || t('flows.configureBlock')}</Text>
            {node.kind === 'condition' ? (
                <>
                    <Handle id="true" type="source" position={Position.Right} style={{top: '35%'}} />
                    <Handle id="false" type="source" position={Position.Right} style={{top: '75%'}} />
                </>
            ) : (
                node.kind !== 'reply' && <Handle type="source" position={Position.Right} />
            )}
        </Box>
    );
}
