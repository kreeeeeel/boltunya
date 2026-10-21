import {Box, Button, Dialog, Flex, Icon, Link, Text, TextInput} from '@gravity-ui/uikit';
import {ArrowUpRightFromSquare, LogoTelegram, ShieldCheck} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import type {ConnectDialogProps} from '@/modules/workspace/ConnectDialog/ConnectDialog.types';
import styles from '@/modules/workspace/ConnectDialog/ConnectDialog.module.css';
import {api, errorMessage} from '@/shared/api/client';
import {FormSkeleton} from '@/shared/ui/FormSkeleton/FormSkeleton';
function validPhone(value: string) {
    return (
        value.startsWith('+') &&
        value.length >= 8 &&
        value.length <= 16 &&
        Array.from(value.slice(1)).every((character) => '0123456789'.includes(character))
    );
}
export function ConnectDialog({status, onClose, onRefresh}: ConnectDialogProps) {
    const t = useTranslations();
    const [apiId, setApiId] = useState('');
    const [apiHash, setApiHash] = useState('');
    const [phone, setPhone] = useState('');
    const [value, setValue] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const state = status?.state ?? 'disconnected';
    const needsValue = ['code', 'password', 'email', 'emailCode'].includes(state);
    const starting = state === 'disconnected' || state === 'error';
    async function submit() {
        setError('');
        if (
            starting &&
            (!apiId ||
                !Array.from(apiId).every((character) => '0123456789'.includes(character)) ||
                apiHash.length !== 32 ||
                !Array.from(apiHash).every((character) =>
                    '0123456789abcdef'.includes(character.toLowerCase()),
                ) ||
                !validPhone(phone.replaceAll(' ', '')))
        ) {
            setError(t('credentialsInvalid'));
            return;
        }
        setBusy(true);
        try {
            if (starting) {
                await api.telegram.connect({
                    credentials: {apiId: Number(apiId), apiHash, phone: phone.replaceAll(' ', '')},
                });
                setApiHash('');
            } else {
                await api.telegram.submit({
                    value: {value},
                });
                setValue('');
            }
            await onRefresh();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    async function disconnect() {
        setBusy(true);
        try {
            await api.telegram.logout();
            await onRefresh();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy(false);
        }
    }
    return (
        <Dialog
            contentOverflow="auto"
            open
            onClose={onClose}
            maxWidth="s"
            fullWidth
            aria-label={t('connect')}
        >
            <Dialog.Header caption={t('connectTitle')} />
            <Dialog.Body>
                <Flex direction="column" gap={4} className={styles.body}>
                    <Flex alignItems="center" gap={3}>
                        <Box className={styles.logo}>
                            <Icon data={LogoTelegram} size={30} />
                        </Box>
                        <Text color="secondary">{t('connectDescription')}</Text>
                    </Flex>
                    {status === undefined && <FormSkeleton />}
                    {starting && status !== undefined && (
                        <>
                            <Flex direction="column" gap={4} className={styles.guide}>
                                {['1', '2', '3'].map((step) => (
                                    <Box key={step}>
                                        <Text className={styles.step}>{t(`step${step}`)}</Text>
                                        <Text className={styles.hint}>{t(`step${step}Text`)}</Text>
                                    </Box>
                                ))}
                                <Link
                                    href="https://my.telegram.org/apps"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    {t('openTelegram')} <Icon data={ArrowUpRightFromSquare} size={13} />
                                </Link>
                            </Flex>
                            <Flex gap={4} className={styles.credentials}>
                                <Box className={styles.field}>
                                    <Text className={styles.label}>{t('apiId')}</Text>
                                    <TextInput
                                        size="l"
                                        type="number"
                                        value={apiId}
                                        onUpdate={setApiId}
                                        controlProps={{'aria-label': t('apiId'), min: 1}}
                                    />
                                </Box>
                                <Box className={styles.field}>
                                    <Text className={styles.label}>{t('apiHash')}</Text>
                                    <TextInput
                                        size="l"
                                        type="password"
                                        value={apiHash}
                                        onUpdate={setApiHash}
                                        controlProps={{
                                            'aria-label': t('apiHash'),
                                            maxLength: 32,
                                            autoComplete: 'off',
                                        }}
                                    />
                                </Box>
                            </Flex>
                            <Box>
                                <Text className={styles.label}>{t('phone')}</Text>
                                <TextInput
                                    size="l"
                                    type="tel"
                                    value={phone}
                                    onUpdate={setPhone}
                                    controlProps={{'aria-label': t('phone'), autoComplete: 'tel'}}
                                />
                                <Text className={styles.hint}>{t('phoneHint')}</Text>
                            </Box>
                        </>
                    )}
                    {needsValue && (
                        <>
                            <Text variant="subheader-2">{t(state)}</Text>
                            <Text className={styles.hint}>
                                {t(state === 'password' ? 'passwordHint' : 'codeHint')}
                            </Text>
                            <TextInput
                                key={state}
                                autoFocus
                                size="xl"
                                type={state === 'password' ? 'password' : 'text'}
                                value={value}
                                onUpdate={setValue}
                                onKeyDown={(event) => {
                                    if (event.key === 'Enter') {
                                        void submit();
                                    }
                                }}
                                controlProps={{
                                    'aria-label': t(state),
                                    autoComplete: state === 'password' ? 'current-password' : 'one-time-code',
                                }}
                            />
                        </>
                    )}
                    {state === 'connecting' && (
                        <Flex direction="column" gap={2}>
                            <Text>{t('waiting')}</Text>
                            <FormSkeleton />
                        </Flex>
                    )}
                    {state === 'ready' && (
                        <Flex direction="column" gap={3}>
                            <Text variant="header-1">{t('authSuccess')}</Text>
                            <Text>{status?.name}</Text>
                            <Button
                                view="outlined-danger"
                                onClick={() => {
                                    if (window.confirm(t('logoutConfirm'))) {
                                        void disconnect();
                                    }
                                }}
                                loading={busy}
                            >
                                {t('disconnect')}
                            </Button>
                        </Flex>
                    )}
                    {(error || status?.error) && <Text color="danger">{error || status?.error}</Text>}
                    <Flex gap={2} className={styles.security}>
                        <Icon data={ShieldCheck} size={17} />
                        <Text>{t('security')}</Text>
                    </Flex>
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={t(state === 'ready' ? 'close' : 'continue')}
                textButtonCancel={t('cancel')}
                onClickButtonCancel={onClose}
                onClickButtonApply={() => {
                    if (state === 'ready') {
                        onClose();
                    } else {
                        void submit();
                    }
                }}
                loading={busy}
                propsButtonApply={{disabled: state === 'connecting' || (needsValue && !value)}}
            >
                {state !== 'ready' && state !== 'disconnected' && (
                    <Button view="flat-danger" onClick={() => void disconnect()} disabled={busy}>
                        {t('disconnect')}
                    </Button>
                )}
            </Dialog.Footer>
        </Dialog>
    );
}
