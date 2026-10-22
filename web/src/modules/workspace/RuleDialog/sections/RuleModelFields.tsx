import {Flex, Select, Skeleton, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {Engine} from '@/shared/api/client';
import type {ModelProfile} from '@/modules/settings/model/useModelProfiles';
import {PromptBudgetNotice} from '@/modules/settings/PromptBudgetNotice/PromptBudgetNotice';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';
import {RuleSelectFilter} from '@/modules/workspace/RuleDialog/components/RuleSelectFilter';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';

export function RuleModelFields({
    value,
    onUpdate,
    engine,
    profiles,
    profileError,
    defaultModel,
    defaultModelProfileId,
}: RuleFieldsProps & {
    readonly engine?: Engine;
    readonly profiles: ModelProfile[];
    readonly profileError: string;
    readonly defaultModel: string;
    readonly defaultModelProfileId?: string;
}) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <Flex direction="column" gap={2}>
                <Text>{t('model')}</Text>
                {!engine ? (
                    <Skeleton height={36} />
                ) : (
                    <Select
                        filterable
                        renderFilter={RuleSelectFilter}
                        popupClassName={styles.selectPopup}
                        width="max"
                        size="l"
                        value={[
                            (value.useDefaultModel ?? true)
                                ? 'default'
                                : value.modelProfileId
                                  ? 'profile:' + value.modelProfileId
                                  : value.model,
                        ]}
                        options={[
                            {
                                value: 'default',
                                content: t('modelDefaultOption', {
                                    name:
                                        profiles.find((profile) => profile.id === defaultModelProfileId)
                                            ?.name ??
                                        engine.models.find((model) => model.id === defaultModel)?.name ??
                                        defaultModel,
                                }),
                            },
                            ...profiles
                                .filter((profile) => profile.enabled)
                                .map((profile) => ({
                                    value: 'profile:' + profile.id,
                                    content: profile.name + ' · ' + profile.model,
                                })),
                            ...engine.models.map((model) => ({
                                value: model.id,
                                content: model.name + (model.installed ? '' : ' · ' + t('notInstalled')),
                            })),
                        ]}
                        onUpdate={([model]) => {
                            if (model) {
                                onUpdate({
                                    ...value,
                                    model:
                                        model === 'default'
                                            ? defaultModel
                                            : model.startsWith('profile:')
                                              ? value.model
                                              : model,
                                    modelProfileId: model.startsWith('profile:') ? model.slice(8) : '',
                                    useDefaultModel: model === 'default',
                                });
                            }
                        }}
                        aria-label={t('model')}
                    />
                )}
                <Text color="secondary">{t('modelDefaultHint')}</Text>
                {profileError && (
                    <Text color="danger" role="alert">
                        {profileError}
                    </Text>
                )}
            </Flex>
            <Flex direction="column" gap={2}>
                <Text>{t('instructions')}</Text>
                <TextArea
                    value={value.instructions}
                    onUpdate={(instructions) => onUpdate({...value, instructions})}
                    minRows={4}
                    maxRows={8}
                    controlProps={{'aria-label': t('instructions'), maxLength: 4000}}
                />
            </Flex>
            <PromptBudgetNotice request={{rule: value}} />
        </Flex>
    );
}
