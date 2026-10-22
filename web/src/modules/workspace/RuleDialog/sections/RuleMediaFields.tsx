import {Flex, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';

export function RuleMediaFields({value, onUpdate}: RuleFieldsProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <Flex direction="column" gap={2}>
                <Switch
                    checked={value.analyzePhotos ?? false}
                    onUpdate={(analyzePhotos) => onUpdate({...value, analyzePhotos})}
                >
                    {t('chatFeatures.analyzePhotos')}
                </Switch>
                <Text color="secondary">{t('chatFeatures.photosHint')}</Text>
            </Flex>
            <Flex direction="column" gap={2}>
                <Switch
                    checked={value.aiStickers ?? false}
                    onUpdate={(aiStickers) => onUpdate({...value, aiStickers})}
                >
                    {t('chatFeatures.aiStickers')}
                </Switch>
                <Text color="secondary">{t('chatFeatures.stickersHint')}</Text>
            </Flex>
        </Flex>
    );
}
