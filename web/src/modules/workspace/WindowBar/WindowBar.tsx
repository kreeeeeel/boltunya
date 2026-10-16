import {Avatar, Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {Copy, Minus, Pause, Play, Square, Xmark} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useEffect, useState} from 'react';

import {errorMessage} from '@/shared/api/client';
import type {AppState} from '@/shared/api/client';
import {desktopBridge} from '@/shared/desktop/bridge';
import type {DesktopWindowAction} from '@/shared/desktop/bridge';
import styles from '@/modules/workspace/WindowBar/WindowBar.module.css';

export function WindowBar({
    state,
    onPause,
    onError,
}: {
    readonly state: AppState | null;
    readonly onPause: () => Promise<void>;
    readonly onError: (message: string) => void;
}) {
    const t = useTranslations();
    const [native, setNative] = useState(false);
    useEffect(() => {
        queueMicrotask(() => setNative(Boolean(desktopBridge())));
    }, []);
    const maximized = state?.window?.maximized ?? false;
    const paused = state?.settings.paused ?? true;
    async function action(command: DesktopWindowAction) {
        try {
            await desktopBridge()?.action(command);
        } catch (cause) {
            onError(errorMessage(cause));
        }
    }
    return (
        <Flex className={styles.bar} alignItems="center" justifyContent="space-between" qa="window-bar">
            <Flex gap={2} alignItems="center" className={styles.brand}>
                <Avatar imgUrl="/assets/brand/boltunya.svg" alt="" size="xs" shape="square" />
                <Text>{t('app')}</Text>
            </Flex>
            <Flex alignItems="center" className={styles.controls}>
                {state?.telegram.state === 'ready' && (
                    <Button
                        size="s"
                        className={styles.ai}
                        view={paused ? 'flat-secondary' : 'flat-info'}
                        onClick={() => void onPause()}
                        aria-label={t(paused ? 'resume' : 'pause')}
                    >
                        <Icon data={paused ? Play : Pause} size={14} />
                        <Text>{t(paused ? 'resume' : 'pause')}</Text>
                    </Button>
                )}
                {native && (
                    <>
                        <Button
                            size="s"
                            view="flat-secondary"
                            className={styles.control}
                            aria-label={t('minimizeWindow')}
                            onClick={() => void action('minimize')}
                        >
                            <Icon data={Minus} size={16} />
                        </Button>
                        <Button
                            size="s"
                            view="flat-secondary"
                            className={styles.control}
                            aria-label={t(maximized ? 'restoreWindow' : 'maximizeWindow')}
                            onClick={() => void action('maximize')}
                        >
                            <Icon data={maximized ? Copy : Square} size={14} />
                        </Button>
                        <Button
                            size="s"
                            view="flat-secondary"
                            className={`${styles.control} ${styles.close}`}
                            aria-label={t('closeWindow')}
                            onClick={() => void action('close')}
                        >
                            <Icon data={Xmark} size={17} />
                        </Button>
                    </>
                )}
            </Flex>
        </Flex>
    );
}
