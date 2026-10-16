'use client';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {addEdge, useEdgesState, useNodesState, type Connection, type Edge} from '@xyflow/react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {AutomationFlow, FlowCanvasNode, FlowNode} from '@/modules/flows/model/flowTypes';

export function useFlowEditor() {
    const t = useTranslations();
    const [flows, setFlows] = useState<AutomationFlow[] | null>(null);
    const [current, setCurrent] = useState<AutomationFlow | null>(null);
    const [nodes, setNodes, onNodesChange] = useNodesState<FlowCanvasNode>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [selected, setSelected] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [test, setTest] = useState('/start');
    const [result, setResult] = useState('');
    const [previewError, setPreviewError] = useState('');
    const [previewBusy, setPreviewBusy] = useState(false);
    useEffect(() => {
        let active = true;
        void api
            .GET('/api/flows')
            .then(dataOrThrow)
            .then((value) => {
                if (active) {
                    setFlows(value);
                }
            })
            .catch((cause) => {
                if (active) {
                    setError(errorMessage(cause));
                }
            });
        return () => {
            active = false;
        };
    }, []);
    function load(flow: AutomationFlow) {
        setCurrent(flow);
        setSelected(flow.nodes[0]?.id ?? '');
        setResult('');
        setPreviewError('');
        setError('');
        setNodes(
            flow.nodes.map((config) => ({
                id: config.id,
                type: 'block',
                position: {x: config.x, y: config.y},
                data: {config},
            })),
        );
        setEdges(
            flow.edges.map((edge) => ({...edge, sourceHandle: edge.output || undefined, type: 'smoothstep'})),
        );
    }
    function draft(): AutomationFlow {
        if (!current) {
            throw new Error(t('flows.selectFlow'));
        }
        return {
            ...current,
            nodes: nodes.map((node) => ({...node.data.config, x: node.position.x, y: node.position.y})),
            edges: edges.map((edge) => ({
                id: edge.id,
                source: edge.source,
                target: edge.target,
                output: edge.sourceHandle ?? '',
            })),
        };
    }
    async function save() {
        if (!current) {
            return;
        }
        setBusy(true);
        setError('');
        try {
            const flow = draft();
            dataOrThrow(
                await api.PUT('/api/flows/{flowId}', {params: {path: {flowId: flow.id}}, body: flow}),
            );
            setFlows((previous) => [...(previous ?? []).filter((item) => item.id !== flow.id), flow]);
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    async function remove() {
        if (!current) {
            return;
        }
        try {
            dataOrThrow(await api.DELETE('/api/flows/{flowId}', {params: {path: {flowId: current.id}}}));
            setFlows((flows ?? []).filter((item) => item.id !== current.id));
            setCurrent(null);
            setNodes([]);
            setEdges([]);
        } catch (cause) {
            setError(errorMessage(cause));
        }
    }
    async function preview() {
        if (!current) {
            return;
        }
        setPreviewBusy(true);
        setPreviewError('');
        setResult('');
        try {
            const reply = dataOrThrow(
                await api.POST('/api/flows/{flowId}/test', {
                    params: {path: {flowId: current.id}},
                    body: {text: test, flow: draft()},
                }),
            );
            setResult(reply.text || t('flows.noReply'));
        } catch (cause) {
            setPreviewError(errorMessage(cause));
        } finally {
            setPreviewBusy(false);
        }
    }
    function add(kind: FlowNode['kind']) {
        const id = crypto.randomUUID(),
            config: FlowNode = {
                id,
                kind,
                text: kind === 'reply' ? '{{reply}}' : '',
                key: 'value',
                operation: 'set',
                match: 'prefix',
                x: 100 + (nodes.length % 3) * 270,
                y: 100 + Math.floor(nodes.length / 3) * 150,
            };
        setNodes((previous) => [
            ...previous,
            {id, type: 'block', position: {x: config.x, y: config.y}, data: {config}},
        ]);
        setSelected(id);
    }
    function connect(connection: Connection) {
        setEdges((previous) => addEdge({...connection, type: 'smoothstep'}, previous));
    }
    const active = nodes.find((node) => node.id === selected)?.data.config;
    return {
        flows,
        setFlows,
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
    };
}
