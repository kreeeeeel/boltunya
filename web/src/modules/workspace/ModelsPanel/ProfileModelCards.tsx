import {Box, Button, Flex, Icon, Label, Text} from '@gravity-ui/uikit';
import {Globe} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {ModelProfile} from '@/modules/settings/model/useModelProfiles';
import styles from '@/modules/workspace/ModelsPanel/ModelsPanel.module.css';
export function ProfileModelCards({
    selected,
    onRefresh,
    profiles,
    error: reportError,
}: {
    readonly selected: string;
    readonly onRefresh: () => Promise<void>;
    readonly profiles: ModelProfile[];
    readonly error: string;
}) {
    const t = useTranslations();
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    async function choose(id: string) {
        setBusy(id);
        setError('');
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: {defaultModelProfileId: id}}));
            await onRefresh();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    return (
        <>
            {(error || reportError) && (
                <Text color="danger" role="alert">
                    {error || reportError}
                </Text>
            )}
            {profiles.length > 0 && (
                <Box className={styles.cards}>
                    {profiles.map((profile) => (
                        <Flex
                            key={profile.id}
                            direction="column"
                            gap={4}
                            qa="profile-model-card"
                            data-profile-id={profile.id}
                            className={`${styles.card} ${selected === profile.id ? styles.selected : ''}`}
                        >
                            <Flex alignItems="center" justifyContent="space-between">
                                <Box className={styles.modelIcon}>
                                    <Icon data={Globe} size={28} />
                                </Box>
                                <Label theme="info">{profile.provider === 'ollama' ? 'Ollama' : 'API'}</Label>
                            </Flex>
                            <Text variant="header-1">{profile.name}</Text>
                            <Text color="secondary">{profile.model}</Text>
                            <Flex direction="column" gap={2}>
                                {[
                                    [t('compareContext'), profile.options.contextWindow.toLocaleString()],
                                    [
                                        t('compareThinking'),
                                        t(profile.capabilities.reasoning ? 'thinkingOn' : 'thinkingOff'),
                                    ],
                                    [
                                        t('modelProfiles.capability.vision'),
                                        t(
                                            profile.capabilities.vision
                                                ? 'imagesSupported'
                                                : 'imagesUnsupported',
                                        ),
                                    ],
                                ].map(([label, value]) => (
                                    <Flex key={label} justifyContent="space-between" gap={3}>
                                        <Text color="secondary">{label}</Text>
                                        <Text>{value}</Text>
                                    </Flex>
                                ))}
                            </Flex>
                            <Button
                                size="l"
                                view={selected === profile.id ? 'outlined-info' : 'action'}
                                loading={busy === profile.id}
                                disabled={!!busy || selected === profile.id}
                                onClick={() => void choose(profile.id)}
                            >
                                {t(selected === profile.id ? 'chosen' : 'choose')}
                            </Button>
                        </Flex>
                    ))}
                </Box>
            )}
        </>
    );
}
