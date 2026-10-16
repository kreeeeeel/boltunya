import type {Node} from '@xyflow/react';
import type {components} from '@/shared/api/schema';

export type AutomationFlow = components['schemas']['AutomationFlow'];
export type FlowNode = components['schemas']['FlowNode'];
export type FlowCanvasNode = Node<{config: FlowNode}, 'block'>;

export function initialFlow(name: string, reply: string): AutomationFlow {
    const id = crypto.randomUUID();
    return {
        id,
        name,
        enabled: false,
        chatIds: [],
        nodes: [
            {
                id: 'command',
                kind: 'command',
                text: '/start',
                key: '',
                operation: 'set',
                match: 'prefix',
                x: 60,
                y: 100,
            },
            {
                id: 'reply',
                kind: 'reply',
                text: reply,
                key: '',
                operation: 'set',
                match: 'prefix',
                x: 380,
                y: 100,
            },
        ],
        edges: [{id: 'start-reply', source: 'command', target: 'reply', output: ''}],
    };
}
