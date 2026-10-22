import {Box, Button, Flex, Loader, Select, Text, TextArea} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {useVoiceComposition} from '@/modules/speech/VoiceComposeDialog/useVoiceComposition';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

export function VoiceComposeFields({
    composition,
}: {
    readonly composition: ReturnType<typeof useVoiceComposition>;
}) {
    const t = useTranslations('voice');
    const disabled = composition.sending || composition.generating;
    return (
        <>
            <Flex direction="column" gap={2}>
                <Text>{t('models.synthesis')}</Text>
                <Select
                    size="l"
                    width="max"
                    filterable
                    value={composition.modelId ? [composition.modelId] : []}
                    disabled={disabled || !composition.models.length}
                    options={composition.models.map((model) => ({value: model.id, content: model.title}))}
                    onUpdate={([id]) => {
                        if (id) {
                            composition.selectModel(id);
                        }
                    }}
                    aria-label={t('models.synthesis')}
                />
                {composition.loadedModels && !composition.ready && (
                    <Text color="secondary">{t('modelsRequired')}</Text>
                )}
                {composition.loadingModels && (
                    <Flex alignItems="center" gap={2} role="status">
                        <Loader size="s" />
                        <Text>{t('manualLoadingModels')}</Text>
                    </Flex>
                )}
                {composition.modelError && (
                    <Flex direction="column" gap={2}>
                        <Text color="danger" role="alert">
                            {composition.modelError}
                        </Text>
                        <Box>
                            <Button view="flat-secondary" onClick={composition.reloadModels}>
                                {t('retry')}
                            </Button>
                        </Box>
                    </Flex>
                )}
                {composition.openSettings && !composition.ready && (
                    <Box>
                        <Button
                            view="flat-secondary"
                            disabled={composition.sending}
                            onClick={composition.openSettings}
                        >
                            {t('openSettings')}
                        </Button>
                    </Box>
                )}
            </Flex>
            <SettingsSlider
                label={t('speechRate')}
                displayValue={composition.speed.toFixed(1) + '×'}
                min={0.5}
                max={2}
                step={0.1}
                value={composition.speed}
                disabled={disabled}
                onUpdate={composition.updateSpeed}
            />
            <Flex direction="column" gap={2}>
                <Flex justifyContent="space-between" gap={3}>
                    <Text>{t('manualText')}</Text>
                    <Text color={composition.tooLong ? 'danger' : 'secondary'}>
                        {composition.text.length} / 4096
                    </Text>
                </Flex>
                <TextArea
                    value={composition.text}
                    onUpdate={composition.updateText}
                    minRows={3}
                    maxRows={6}
                    disabled={disabled}
                    controlProps={{'aria-label': t('manualText')}}
                />
            </Flex>
        </>
    );
}
