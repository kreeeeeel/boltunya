import {Flex, Select, Switch, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {AppState} from '@/shared/api/client';
import {communicationStyles} from '@/modules/workspace/model/communicationStyles';
import {StyleOption} from '@/modules/workspace/StyleOption/StyleOption';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

export function RuleToneFields({
    value,
    onUpdate,
    customStyles,
}: RuleFieldsProps & {readonly customStyles: NonNullable<AppState['settings']['customStyles']>}) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <Flex direction="column" gap={2}>
                <Text>{t('style')}</Text>
                <Select
                    width="max"
                    size="l"
                    controlClassName={styles.styleControl}
                    renderOption={(option) => <>{option.content}</>}
                    renderSelectedOption={(option) => <>{option.content}</>}
                    value={[value.styleId ? 'preset:' + value.styleId : value.style]}
                    options={[
                        ...communicationStyles.map(({value, emoji}) => ({
                            value,
                            text: t(value),
                            content: <StyleOption emoji={emoji} name={t(value)} />,
                        })),
                        ...customStyles.map((style) => ({
                            value: 'preset:' + style.id,
                            text: style.name,
                            content: <StyleOption emoji={style.emoji} name={style.name} />,
                        })),
                    ]}
                    onUpdate={([styleValue]) => {
                        if (styleValue?.startsWith('preset:')) {
                            onUpdate({...value, style: 'custom', styleId: styleValue.slice(7)});
                            return;
                        }
                        const style = communicationStyles.find(
                            (option) => option.value === styleValue,
                        )?.value;
                        if (style) {
                            onUpdate({...value, style, styleId: ''});
                        }
                    }}
                    aria-label={t('style')}
                />
            </Flex>
            <Flex direction="column" gap={2}>
                <Switch
                    checked={value.matchMyStyle ?? true}
                    onUpdate={(matchMyStyle) => onUpdate({...value, matchMyStyle})}
                >
                    {t('matchMyStyle')}
                </Switch>
                <Text color="secondary">{t('matchMyStyleHint')}</Text>
            </Flex>
            <SettingsSlider
                label={t('creativity')}
                displayValue={value.creativity + '%'}
                value={value.creativity}
                min={0}
                max={100}
                onUpdate={(creativity) => onUpdate({...value, creativity})}
            />
            <Switch checked={value.emoji} onUpdate={(emoji) => onUpdate({...value, emoji})}>
                {t('emoji')}
            </Switch>
        </Flex>
    );
}
