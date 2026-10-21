import {Box, Button, Flex, Select, Switch, Text, TextArea, TextInput} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import type {ModelProfileUpdate} from '@/modules/settings/model/useModelProfiles';
import styles from '@/modules/settings/ProviderProfiles/ProviderProfiles.module.css';
const numericOptions = [
    'temperature',
    'topP',
    'topK',
    'presencePenalty',
    'frequencyPenalty',
    'repeatPenalty',
    'seed',
    'maxOutputTokens',
    'contextWindow',
    'reasoningTokens',
    'connectTimeoutSeconds',
    'idleTimeoutSeconds',
    'totalTimeoutSeconds',
    'keepAliveMinutes',
] as const;
const capabilityOptions = [
    'streaming',
    'vision',
    'structuredOutput',
    'reasoning',
    'temperature',
    'topP',
    'topK',
    'penalties',
] as const;
export function ProfileOptions({
    value,
    onUpdate,
}: {
    readonly value: ModelProfileUpdate;
    readonly onUpdate: (value: ModelProfileUpdate) => void;
}) {
    const t = useTranslations('modelProfiles');
    const [expanded, setExpanded] = useState(false);
    return (
        <Flex direction="column" gap={4}>
            <Button view="flat-secondary" onClick={() => setExpanded(!expanded)} aria-expanded={expanded}>
                {t('advanced')}
            </Button>
            {expanded && (
                <>
                    <Text color="secondary">{t('advancedHint')}</Text>
                    <Box className={styles.fields}>
                        {numericOptions.map((key) => (
                            <Flex direction="column" gap={2} key={key}>
                                <Text>{t('options.' + key)}</Text>
                                <TextInput
                                    size="l"
                                    type="number"
                                    disabled={
                                        (value.provider !== 'ollama' &&
                                            ['topK', 'repeatPenalty', 'keepAliveMinutes'].includes(key)) ||
                                        (key === 'temperature' && !value.capabilities.temperature) ||
                                        (key === 'topP' && !value.capabilities.topP) ||
                                        (key === 'topK' && !value.capabilities.topK) ||
                                        (['presencePenalty', 'frequencyPenalty', 'repeatPenalty'].includes(
                                            key,
                                        ) &&
                                            !value.capabilities.penalties) ||
                                        (key === 'reasoningTokens' && !value.capabilities.reasoning)
                                    }
                                    value={value.options[key]?.toString() ?? ''}
                                    onUpdate={(text) =>
                                        onUpdate({
                                            ...value,
                                            options: {
                                                ...value.options,
                                                [key]: text === '' ? null : Number(text),
                                            },
                                        })
                                    }
                                    controlProps={{
                                        'aria-label': t('options.' + key),
                                        step: [
                                            'temperature',
                                            'topP',
                                            'presencePenalty',
                                            'frequencyPenalty',
                                            'repeatPenalty',
                                        ].includes(key)
                                            ? '0.1'
                                            : '1',
                                    }}
                                />
                            </Flex>
                        ))}
                    </Box>
                    <Flex direction="column" gap={2}>
                        <Text>{t('thinking')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[value.options.thinking]}
                            options={['default', 'enabled', 'disabled'].map((option) => ({
                                value: option,
                                content: t('thinkingModes.' + option),
                            }))}
                            onUpdate={([thinking]) =>
                                onUpdate({
                                    ...value,
                                    options: {
                                        ...value.options,
                                        thinking: thinking as ModelProfileUpdate['options']['thinking'],
                                    },
                                })
                            }
                            aria-label={t('thinking')}
                        />
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('reasoningEffort')}</Text>
                        <TextInput
                            size="l"
                            value={value.options.reasoningEffort}
                            onUpdate={(reasoningEffort) =>
                                onUpdate({...value, options: {...value.options, reasoningEffort}})
                            }
                            controlProps={{'aria-label': t('reasoningEffort')}}
                        />
                    </Flex>
                    <Flex direction="column" gap={2}>
                        <Text>{t('stop')}</Text>
                        <TextArea
                            minRows={2}
                            value={value.options.stop.join('\n')}
                            onUpdate={(stop) =>
                                onUpdate({
                                    ...value,
                                    options: {...value.options, stop: stop.split('\n').filter(Boolean)},
                                })
                            }
                            controlProps={{'aria-label': t('stop')}}
                        />
                    </Flex>
                    <Text variant="subheader-2">{t('capabilities')}</Text>
                    <Text color="secondary">{t('capabilitiesHint')}</Text>
                    <Box className={styles.fields}>
                        {capabilityOptions.map((key) => (
                            <Switch
                                key={key}
                                checked={value.capabilities[key]}
                                onUpdate={(checked) =>
                                    onUpdate({
                                        ...value,
                                        capabilities: {...value.capabilities, [key]: checked},
                                    })
                                }
                            >
                                {t('capability.' + key)}
                            </Switch>
                        ))}
                    </Box>
                    <Flex direction="column" gap={2}>
                        <Text>{t('tokenLimit')}</Text>
                        <Select
                            size="l"
                            width="max"
                            value={[value.capabilities.tokenLimit]}
                            options={['max_tokens', 'max_completion_tokens'].map((option) => ({
                                value: option,
                                content: option,
                            }))}
                            onUpdate={([tokenLimit]) =>
                                onUpdate({
                                    ...value,
                                    capabilities: {
                                        ...value.capabilities,
                                        tokenLimit:
                                            tokenLimit as ModelProfileUpdate['capabilities']['tokenLimit'],
                                    },
                                })
                            }
                            aria-label={t('tokenLimit')}
                        />
                    </Flex>
                </>
            )}
        </Flex>
    );
}
