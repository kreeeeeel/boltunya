'use client';

import {Box, Button, Flex, Icon, Select, Switch, Text, TextInput} from '@gravity-ui/uikit';
import {Plus, TrashBin, Play, Pencil, ArrowLeft} from '@gravity-ui/icons';
import {useEffect, useRef, useState} from 'react';
import {ReactFlow, Background, Controls, MiniMap, type ReactFlowInstance} from '@xyflow/react';
import {useTranslations} from 'next-intl';
import type {Chat} from '@/shared/api/client';
import {FlowBlock} from '@/modules/flows/FlowBlock/FlowBlock';
import {FlowInspector} from '@/modules/flows/FlowInspector/FlowInspector';
import {FlowTestDialog} from '@/modules/flows/FlowTestDialog/FlowTestDialog';
import {FlowLibrary} from '@/modules/flows/FlowLibrary/FlowLibrary';
import {initialFlow, type FlowCanvasNode} from '@/modules/flows/model/flowTypes';
import {useFlowEditor} from '@/modules/flows/model/useFlowEditor';
import styles from '@/modules/flows/FlowEditor/FlowEditor.module.css';

const nodeTypes = {block: FlowBlock};

export function FlowEditor({chats}: {readonly chats: Chat[]}) {
    const t = useTranslations();
    const [testing, setTesting] = useState(false);
    const [renaming, setRenaming] = useState(false);
    const canvas = useRef<HTMLDivElement>(null);
    const flowView = useRef<ReactFlowInstance<FlowCanvasNode>>(null);
    const {
        flows,
        current,
        setCurrent,
        nodes,
        setNodes,
        onNodesChange,
        edges,
        setEdges,
        onEdgesChange,
        selected,
        setSelected,
        error,
        busy,
        test,
        setTest,
        result,
        previewError,
        previewBusy,
        load,
        save,
        remove,
        preview,
        add,
        connect,
        active,
    } = useFlowEditor();
    useEffect(() => {
        const element = canvas.current;
        if (!element) {
            return;
        }
        let timer: ReturnType<typeof setTimeout>;
        const observer = new ResizeObserver(() => {
            clearTimeout(timer);
            timer = setTimeout(
                () => void flowView.current?.fitView({maxZoom: 1, padding: 0.3, duration: 120}),
                80,
            );
        });
        observer.observe(element);
        return () => {
            observer.disconnect();
            clearTimeout(timer);
        };
    }, [current?.id]);
    if (!flows && !error) {
        return (
            <Flex direction="column" className={styles.root}>
                <FlowLibrary loading flows={[]} onCreate={create} onOpen={load} />
            </Flex>
        );
    }
    function create() {
        load(initialFlow(t('flows.newFlow'), t('flows.welcome')));
        setRenaming(true);
    }
    if (!current) {
        return (
            <Flex direction="column" className={styles.root} qa="flow-editor">
                {error && (
                    <Text color="danger" className={styles.error}>
                        {error}
                    </Text>
                )}
                <FlowLibrary flows={flows ?? []} onCreate={create} onOpen={load} />
            </Flex>
        );
    }
    return (
        <Flex direction="column" className={styles.root} qa="flow-editor">
            <Flex className={styles.toolbar} alignItems="center" gap={3} qa="flow-toolbar">
                <Button
                    view="flat-secondary"
                    aria-label={t('flows.back')}
                    onClick={() => {
                        setCurrent(null);
                        setRenaming(false);
                    }}
                >
                    <Icon data={ArrowLeft} size={18} />
                </Button>
                <Flex alignItems="center" gap={1} className={styles.selector}>
                    {renaming && current ? (
                        <TextInput
                            autoFocus
                            value={current.name}
                            onUpdate={(name) => setCurrent({...current, name})}
                            className={styles.name}
                            controlProps={{
                                'aria-label': t('flows.name'),
                                maxLength: 80,
                                onBlur: () => setRenaming(false),
                                onKeyDown: (event) => {
                                    if (event.key === 'Enter') {
                                        setRenaming(false);
                                    }
                                },
                            }}
                        />
                    ) : (
                        <Select
                            width="max"
                            options={(flows?.some((flow) => flow.id === current.id)
                                ? flows
                                : [...(flows ?? []), current]
                            ).map((flow) => ({
                                value: flow.id,
                                content: current?.id === flow.id ? current.name : flow.name,
                            }))}
                            value={current ? [current.id] : []}
                            placeholder={t('flows.selectFlow')}
                            onUpdate={([id]) => {
                                const flow = flows?.find((item) => item.id === id);
                                if (flow) {
                                    load(flow);
                                }
                            }}
                            aria-label={t('flows.selectFlow')}
                        />
                    )}
                    {current && (
                        <Button
                            view="flat-secondary"
                            aria-label={t('flows.name')}
                            onClick={() => setRenaming(true)}
                        >
                            <Icon data={Pencil} size={16} />
                        </Button>
                    )}
                    <Button view="flat-secondary" aria-label={t('flows.create')} onClick={create}>
                        <Icon data={Plus} size={16} />
                    </Button>
                </Flex>
                {current && (
                    <>
                        <Flex alignItems="center" gap={2} className={styles.actions}>
                            <Switch
                                checked={current.enabled}
                                onUpdate={(enabled) => setCurrent({...current, enabled})}
                            >
                                {t('flows.enabled')}
                            </Switch>
                            <Button onClick={() => setTesting(true)} disabled={busy}>
                                <Icon data={Play} size={16} />
                                {t('flows.test')}
                            </Button>
                            <Button view="action" loading={busy} onClick={() => void save()}>
                                {t('save')}
                            </Button>
                        </Flex>
                        <Button
                            view="flat-danger"
                            aria-label={t('flows.delete')}
                            onClick={() => void remove()}
                        >
                            <Icon data={TrashBin} size={18} />
                        </Button>
                    </>
                )}
            </Flex>
            {error && (
                <Text color="danger" className={styles.error}>
                    {error}
                </Text>
            )}
            <>
                <Flex className={styles.palette} alignItems="center" gap={2} wrap qa="flow-palette">
                    {(['command', 'agent', 'reply', 'store', 'condition'] as const).map((kind) => (
                        <Button
                            key={kind}
                            size="m"
                            disabled={
                                kind === 'command' &&
                                nodes.some((node) => node.data.config.kind === 'command')
                            }
                            onClick={() => add(kind)}
                        >
                            {t('flows.kind.' + kind)}
                        </Button>
                    ))}
                    <Select
                        multiple
                        width={200}
                        value={current.chatIds}
                        options={chats.map((chat) => ({value: chat.id, content: chat.title}))}
                        onUpdate={(chatIds) => setCurrent({...current, chatIds})}
                        placeholder={t('flows.allChats')}
                        aria-label={t('flows.chats')}
                    />
                </Flex>
                <Flex className={styles.workspace}>
                    <Box ref={canvas} className={styles.canvas} qa="flow-canvas">
                        <ReactFlow
                            nodes={nodes}
                            onInit={(instance) => {
                                flowView.current = instance;
                            }}
                            edges={edges}
                            onNodesChange={onNodesChange}
                            onEdgesChange={onEdgesChange}
                            onConnect={connect}
                            onNodeClick={(_event, node) => setSelected(node.id)}
                            onPaneClick={() => setSelected('')}
                            nodeTypes={nodeTypes}
                            fitView
                            fitViewOptions={{maxZoom: 1, padding: 0.3}}
                            minZoom={0.3}
                            maxZoom={1.5}
                            deleteKeyCode={['Backspace', 'Delete']}
                        >
                            <Background color="var(--tiho-scrollbar)" gap={24} />
                            <Controls showInteractive={false} />
                            <MiniMap
                                nodeColor="var(--g-color-text-brand)"
                                maskColor="transparent"
                                style={{width: 140, height: 100}}
                            />
                        </ReactFlow>
                    </Box>
                    <FlowInspector
                        node={active}
                        onUpdate={(config) =>
                            setNodes((previous) =>
                                previous.map((node) =>
                                    node.id === config.id ? {...node, data: {config}} : node,
                                ),
                            )
                        }
                        onDelete={() => {
                            setNodes((previous) => previous.filter((node) => node.id !== selected));
                            setEdges((previous) =>
                                previous.filter(
                                    (edge) => edge.source !== selected && edge.target !== selected,
                                ),
                            );
                            setSelected('');
                        }}
                    />
                </Flex>
                {testing && (
                    <FlowTestDialog
                        text={test}
                        onUpdate={setTest}
                        result={result}
                        error={previewError}
                        busy={previewBusy}
                        onRun={() => void preview()}
                        onClose={() => setTesting(false)}
                    />
                )}
            </>
        </Flex>
    );
}
