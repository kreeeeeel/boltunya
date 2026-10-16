import {Button, Disclosure, Flex, Link, Skeleton, Text, TextInput} from '@gravity-ui/uikit';
import {useEffect, useState} from 'react';
import {useTranslations} from 'next-intl';

import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {components} from '@/shared/api/schema';

export function SearchConnectionPanel() {
    const t = useTranslations('internetSearch');
    const [connection, setConnection] = useState<components['schemas']['SearchConnection']>();
    const [key, setKey] = useState('');
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        void api
            .GET('/api/search/connection')
            .then(dataOrThrow)
            .then((value) => {
                if (active) {
                    setConnection(value);
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

    async function update(action: 'save' | 'remove' | 'check' | 'refresh') {
        setBusy(action);
        setError('');
        try {
            const result =
                action === 'save'
                    ? await api.POST('/api/search/connection', {body: {apiKey: key.trim()}})
                    : action === 'remove'
                      ? await api.DELETE('/api/search/connection')
                      : action === 'check'
                        ? await api.POST('/api/search/check')
                        : await api.GET('/api/search/connection');
            setConnection(dataOrThrow(result));
            if (action === 'save' || action === 'remove') {
                setKey('');
            }
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }

    return (
        <Flex direction="column" gap={3} qa="search-connection">
            <Text variant="subheader-2">{t('title')}</Text>
            <Text color="secondary">{t('hint')}</Text>
            <Text>{t('free')}</Text>
            <Disclosure summary={t('optionalExa')} size="l">
                <Flex direction="column" gap={3}>
                    {!connection && !error && <Skeleton height={56} />}
                    {connection && (
                        <>
                            <Text>{t(connection.configured ? 'configured' : 'keyless')}</Text>
                            <Text
                                color={
                                    connection.state === 'limited' || connection.state === 'unavailable'
                                        ? 'warning'
                                        : 'secondary'
                                }
                                role="status"
                            >
                                {t('states.' + connection.state)}
                            </Text>
                        </>
                    )}
                    <Text>{t('key')}</Text>
                    <TextInput
                        type="password"
                        size="l"
                        value={key}
                        onUpdate={setKey}
                        disabled={!!busy}
                        controlProps={{'aria-label': t('key'), autoComplete: 'off', maxLength: 256}}
                    />
                    <Link href="https://dashboard.exa.ai/api-keys" target="_blank" rel="noreferrer">
                        {t('getKey')}
                    </Link>
                    <Flex gap={2} wrap>
                        <Button
                            view="action"
                            disabled={!!busy || key.trim().length < 8}
                            loading={busy === 'save'}
                            onClick={() => void update('save')}
                        >
                            {t('save')}
                        </Button>
                        {connection?.configured && (
                            <Button
                                disabled={!!busy}
                                loading={busy === 'remove'}
                                onClick={() => void update('remove')}
                            >
                                {t('remove')}
                            </Button>
                        )}
                        <Button
                            disabled={!!busy}
                            loading={busy === 'check'}
                            onClick={() => void update('check')}
                        >
                            {t('check')}
                        </Button>
                        {error && (
                            <Button disabled={!!busy} onClick={() => void update('refresh')}>
                                {t('refresh')}
                            </Button>
                        )}
                    </Flex>
                    {error && (
                        <Text color="danger" role="alert">
                            {error}
                        </Text>
                    )}
                </Flex>
            </Disclosure>
        </Flex>
    );
}
