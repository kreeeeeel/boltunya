import {Box, Button, Flex, Icon, Select, Text, TextArea, TextInput} from '@gravity-ui/uikit';
import {Check} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import {SettingsSection} from '@/modules/settings/SettingsSection/SettingsSection';
import {usePreferences} from '@/shared/theme/Preferences';
import {defaultAppearance, themeTokens, compileAppearance, validThemeColor} from '@/shared/theme/appearance';
import {errorMessage} from '@/shared/api/client';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import styles from '@/modules/settings/AppearanceSettings/AppearanceSettings.module.css';
export function AppearanceSettings() {
    const t = useTranslations('appearance');
    const common = useTranslations();
    const preferences = usePreferences();
    const [value, setValue] = useState(preferences.appearance);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const feedback = useSaveFeedback(value);
    function preview() {
        try {
            compileAppearance(value);
            preferences.previewAppearance(value);
            setError('');
        } catch (cause) {
            setError(t(errorMessage(cause)));
        }
    }
    async function save() {
        setBusy(true);
        setError('');
        feedback.clearSaved();
        try {
            compileAppearance(value);
            await preferences.saveAppearance(value);
            feedback.markSaved();
        } catch (cause) {
            const message = errorMessage(cause);
            setError(
                ['invalidTheme', 'invalidColor', 'invalidCss', 'unsafeCss'].includes(message)
                    ? t(message)
                    : message,
            );
        } finally {
            setBusy(false);
        }
    }
    return (
        <SettingsSection
            id="appearance"
            actions={
                <>
                    <Button
                        size="l"
                        view="flat-secondary"
                        onClick={() => {
                            setValue(defaultAppearance);
                            preferences.previewAppearance(defaultAppearance);
                            setError('');
                        }}
                    >
                        {t('reset')}
                    </Button>
                    <Button size="l" view="outlined" onClick={preview}>
                        {t('preview')}
                    </Button>
                    <Button
                        size="l"
                        view="action"
                        loading={busy}
                        onClick={() => void save()}
                        aria-label={common(feedback.saved ? 'saved' : 'save')}
                    >
                        {feedback.saved && <Icon data={Check} />}
                        <Text role={feedback.saved ? 'status' : undefined}>
                            {common(feedback.saved ? 'saved' : 'save')}
                        </Text>
                    </Button>
                </>
            }
        >
            <Flex direction="column" gap={2}>
                <Text variant="subheader-2">{t('mode')}</Text>
                <Select
                    size="l"
                    width="max"
                    value={[value.mode]}
                    options={['system', 'light', 'dark'].map((mode) => ({
                        value: mode,
                        content: t('modes.' + mode),
                    }))}
                    onUpdate={([mode]) => setValue({...value, mode: mode as typeof value.mode})}
                    aria-label={t('mode')}
                />
            </Flex>
            <Flex direction="column" gap={3}>
                <Text variant="subheader-2">{t('colors')}</Text>
                <Text color="secondary">{t('colorsHint')}</Text>
                <Box className={styles.colors}>
                    {Object.keys(themeTokens).map((token) => (
                        <Flex key={token} direction="column" gap={2}>
                            <Text>{t('tokens.' + token)}</Text>
                            <TextInput
                                size="l"
                                value={value.tokens[token] ?? ''}
                                onUpdate={(color) =>
                                    setValue({
                                        ...value,
                                        tokens: color
                                            ? {...value.tokens, [token]: color}
                                            : Object.fromEntries(
                                                  Object.entries(value.tokens).filter(
                                                      ([name]) => name !== token,
                                                  ),
                                              ),
                                    })
                                }
                                startContent={
                                    <Box
                                        className={styles.swatch}
                                        style={{
                                            background: validThemeColor(value.tokens[token] ?? '')
                                                ? value.tokens[token]
                                                : 'transparent',
                                        }}
                                    />
                                }
                                placeholder={t('defaultColor')}
                                controlProps={{'aria-label': t('tokens.' + token), maxLength: 9}}
                            />
                        </Flex>
                    ))}
                </Box>
            </Flex>
            <Flex direction="column" gap={2}>
                <Text variant="subheader-2">{t('css')}</Text>
                <Text color="secondary">{t('cssHint')}</Text>
                <TextArea
                    value={value.css}
                    onUpdate={(css) => setValue({...value, css})}
                    minRows={9}
                    maxRows={18}
                    controlProps={{'aria-label': t('css'), maxLength: 20000, spellCheck: false}}
                />
                <Text color="secondary">{t('cssScope')}</Text>
            </Flex>
            <Button
                view="flat-secondary"
                onClick={() => {
                    setValue(preferences.appearance);
                    preferences.previewAppearance(preferences.appearance);
                    setError('');
                }}
            >
                {t('revert')}
            </Button>
            {(error || preferences.appearanceError) && (
                <Text color="danger" role="alert">
                    {error || preferences.appearanceError}
                </Text>
            )}
        </SettingsSection>
    );
}
