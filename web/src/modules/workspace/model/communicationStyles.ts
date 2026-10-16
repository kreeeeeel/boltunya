import type {ChatRule} from '@/shared/api/client';

export const communicationStyles: {value: ChatRule['style']; emoji: string}[] = [
    {value: 'friendly', emoji: '🙂'},
    {value: 'business', emoji: '💼'},
    {value: 'concise', emoji: '⚡'},
    {value: 'warm', emoji: '🫶'},
    {value: 'aggressive', emoji: '😠'},
    {value: 'rough', emoji: '🤬'},
    {value: 'custom', emoji: '✍️'},
];
