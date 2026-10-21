import {FolderOpen} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Skeleton, Text, Tooltip} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, errorMessage} from '@/shared/api/client';
import type * as Contract from '@aigram/api-client';
import {desktopBridge} from '@/shared/desktop/bridge';
import styles from '@/modules/storage/StorageLocations/StorageLocations.module.css';
export function StorageLocations() {
    const t = useTranslations('storageLocations');
    const [locations, setLocations] = useState<Contract.StorageLocation[]>();
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const desktop = desktopBridge();
    useEffect(() => {
        let active = true;
        void api.system
            .storageLocations()
            .then((value) => {
                if (active) {
                    setLocations(value.locations);
                }
            })
            .catch((cause) => {
                if (active) {
                    setError(errorMessage(cause));
                }
            });
        return () => {
            active = false;
        };
    }, []);
    async function open(id: string) {
        setBusy(id);
        setError('');
        try {
            await desktop?.openStorageLocation(id);
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }
    return (
        <Flex direction="column" gap={3} qa="storage-locations">
            <Text variant="subheader-2">{t('title')}</Text>
            <Text color="secondary">{t('description')}</Text>
            {!locations && !error && <Skeleton height={180} />}
            {locations?.map((location) => (
                <Flex key={location.id} gap={3} alignItems="center" className={styles.location}>
                    <Box className={styles.details}>
                        <Text className={styles.label}>{t('names.' + location.id)}</Text>
                        <Text color="secondary" className={styles.path}>
                            {location.path || t('external')}
                        </Text>
                        {location.note && (
                            <Text color="secondary" className={styles.note}>
                                {location.note}
                            </Text>
                        )}
                    </Box>
                    <Tooltip content={!desktop ? t('desktopOnly') : t('open')}>
                        <Button
                            view="flat-secondary"
                            size="l"
                            aria-label={t('openNamed', {name: t('names.' + location.id)})}
                            disabled={!desktop || !location.openable || !!busy}
                            loading={busy === location.id}
                            onClick={() => void open(location.id)}
                        >
                            <Icon data={FolderOpen} size={20} />
                        </Button>
                    </Tooltip>
                </Flex>
            ))}
            {error && (
                <Text color="danger" role="alert">
                    {error}
                </Text>
            )}
        </Flex>
    );
}
