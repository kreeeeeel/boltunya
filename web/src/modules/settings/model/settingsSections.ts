export const settingsGroups = [
    {id: 'application', sections: ['application']},
    {id: 'assistant', sections: ['replies', 'voice', 'proactive', 'prompt', 'styles']},
    {id: 'intelligence', sections: ['providers', 'routing', 'compute']},
    {id: 'memory', sections: ['memory', 'collection']},
    {id: 'appearance', sections: ['language']},
    {id: 'data', sections: ['media', 'cache', 'storage']},
] as const;

export type SettingsSectionId = (typeof settingsGroups)[number]['sections'][number];

export const settingsSections = settingsGroups.flatMap((group) => [...group.sections]);
