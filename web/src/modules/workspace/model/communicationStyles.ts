import type {ChatRule} from '@/shared/api/client';

export const communicationStyles: {value: Exclude<ChatRule['style'], 'custom'>; emoji: string}[] = [
    {value: 'friendly', emoji: '🙂'},
    {value: 'business', emoji: '💼'},
    {value: 'concise', emoji: '⚡'},
    {value: 'warm', emoji: '🫶'},
    {value: 'aggressive', emoji: '😠'},
    {value: 'rough', emoji: '🤬'},
];
