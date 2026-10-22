import {
    BookOpen,
    Comment,
    Comments,
    Microphone,
    Palette,
    Picture,
    Play,
    Sliders,
    Persons,
} from '@gravity-ui/icons';

export const ruleSections = [
    'replies',
    'model',
    'tone',
    'voice',
    'proactive',
    'preview',
    'memory',
    'media',
    'groups',
] as const;

export type RuleSectionId = (typeof ruleSections)[number];

export const ruleSectionGroups = [
    {id: 'assistant', sections: ['replies', 'model', 'tone', 'voice', 'proactive', 'preview']},
    {id: 'context', sections: ['memory', 'media', 'groups']},
] as const;

export const ruleSectionIcons = {
    replies: Comments,
    model: Sliders,
    tone: Palette,
    voice: Microphone,
    proactive: Comment,
    preview: Play,
    memory: BookOpen,
    media: Picture,
    groups: Persons,
} satisfies Record<RuleSectionId, typeof Comments>;
