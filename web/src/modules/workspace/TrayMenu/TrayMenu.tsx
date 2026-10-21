'use client';
import {Box, Icon, Menu, Text} from '@gravity-ui/uikit';
import {ArrowUpRightFromSquare, Pause, Play, Sliders, Xmark} from '@gravity-ui/icons';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import styles from '@/modules/workspace/TrayMenu/TrayMenu.module.css';
import {trayBridge} from '@/shared/desktop/bridge';

export function TrayMenu() {
    const t = useTranslations();
    const [paused, setPaused] = useState(true);
    const [error, setError] = useState('');
    function command(action: string) {
        void trayBridge()?.action(action);
    }
    useEffect(() => {
        const refresh = async () => {
            try {
                setPaused(dataOrThrow(await api.GET('/api/state')).settings.paused);
                setError('');
            } catch (cause) {
                setError(errorMessage(cause));
            }
        };
        const keydown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                command('hide');
            }
        };
        void refresh();
        window.addEventListener('boltun-tray-refresh', refresh);
        window.addEventListener('keydown', keydown);
        return () => {
            window.removeEventListener('boltun-tray-refresh', refresh);
            window.removeEventListener('keydown', keydown);
        };
    }, []);
    return (
        <Box className={styles.root} qa="tray-menu">
            <Menu size="m">
                <Menu.Item
                    iconStart={<Icon data={ArrowUpRightFromSquare} size={18} />}
                    onClick={() => command('show')}
                >
                    {t('openWindow')}
                </Menu.Item>
                <Menu.Group>
                    <Menu.Item
                        iconStart={<Icon data={paused ? Play : Pause} size={18} />}
                        onClick={() => {
                            setPaused(!paused);
                            command('pause');
                        }}
                    >
                        {t(paused ? 'trayResume' : 'trayPause')}
                    </Menu.Item>
                    <Menu.Item
                        iconStart={<Icon data={Sliders} size={18} />}
                        onClick={() => command('settings')}
                    >
                        {t('settings')}
                    </Menu.Item>
                </Menu.Group>
                <Menu.Item iconStart={<Icon data={Xmark} size={18} />} onClick={() => command('quit')}>
                    {t('trayQuit')}
                </Menu.Item>
            </Menu>
            {error && <Text color="danger">{error}</Text>}
        </Box>
    );
}
