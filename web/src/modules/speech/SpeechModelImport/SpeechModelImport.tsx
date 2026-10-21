import {ArrowUpFromLine, Check, File as FileIcon, Xmark} from '@gravity-ui/icons';
import {Box, Button, Flex, Icon, Progress, Text, TextInput} from '@gravity-ui/uikit';
import {useEffect, useRef, useState} from 'react';
import {useTranslations} from 'next-intl';
import {api, errorMessage} from '@/shared/api/client';
import {ApiRequestError} from '@/shared/api/ApiRequestError';
import {MAX_UPLOAD_BYTES, uploadFile} from '@/modules/media/model/uploads';
import styles from '@/modules/speech/SpeechModelImport/SpeechModelImport.module.css';

export function SpeechModelImport({onImported}: {readonly onImported: () => void}) {
    const t = useTranslations('voice');
    const input = useRef<HTMLInputElement | null>(null);
    const pending = useRef<AbortController | null>(null);
    const mounted = useRef(false);
    const uploaded = useRef('');
    const [file, setFile] = useState<File>();
    const [title, setTitle] = useState('');
    const [language, setLanguage] = useState('');
    const [license, setLicense] = useState('');
    const [phase, setPhase] = useState<'idle' | 'uploading' | 'importing' | 'ready'>('idle');
    const [progress, setProgress] = useState(0);
    const [error, setError] = useState('');
    const busy = phase === 'uploading' || phase === 'importing';

    async function cleanUpload() {
        const id = uploaded.current;
        uploaded.current = '';
        if (id) {
            try {
                await api.media.deleteUpload({id});
            } catch (cause) {
                console.error('Temporary speech model cleanup failed.', errorMessage(cause));
            }
        }
    }

    useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            pending.current?.abort();
            void cleanUpload();
        };
    }, []);

    function selectFile(next: File | undefined) {
        setError('');
        setPhase('idle');
        if (!next) {
            return;
        }
        if (!next.name.toLowerCase().endsWith('.tar.bz2')) {
            setFile(undefined);
            setError(t('importUnsupported'));
            return;
        }
        if (next.size <= 0 || next.size > MAX_UPLOAD_BYTES) {
            setFile(undefined);
            setError(t('importTooLarge'));
            return;
        }
        setFile(next);
    }

    async function submit() {
        if (
            busy ||
            !file ||
            !title.trim() ||
            title.length > 80 ||
            language.length > 10 ||
            license.length > 512
        ) {
            return;
        }
        const controller = new AbortController();
        pending.current = controller;
        setError('');
        setProgress(0);
        setPhase('uploading');
        try {
            const upload = await uploadFile(file, controller.signal, (loaded) => {
                if (mounted.current && !controller.signal.aborted) {
                    setProgress(Math.min(100, Math.round((loaded * 100) / file.size)));
                }
            });
            uploaded.current = upload.id;
            controller.signal.throwIfAborted();
            setPhase('importing');
            await api.speech.importSpeechModel(
                {
                    speechModelImportRequest: {
                        uploadId: upload.id,
                        title: title.trim(),
                        ...(language.trim() ? {language: language.trim()} : {}),
                        ...(license.trim() ? {license: license.trim()} : {}),
                    },
                },
                {signal: controller.signal},
            );
            uploaded.current = '';
            if (mounted.current && !controller.signal.aborted) {
                setPhase('ready');
                setFile(undefined);
                if (input.current) {
                    input.current.value = '';
                }
                onImported();
            }
        } catch (cause) {
            await cleanUpload();
            if (mounted.current && !controller.signal.aborted) {
                setPhase('idle');
                setError(cause instanceof ApiRequestError ? t('importFailed') : errorMessage(cause));
            }
        } finally {
            if (pending.current === controller) {
                pending.current = null;
            }
            if (mounted.current && controller.signal.aborted) {
                setPhase('idle');
            }
        }
    }

    return (
        <Flex direction="column" gap={3} className={styles.root} qa="speech-model-import">
            <Text color="secondary">{t('importHint')}</Text>
            <Box
                as="input"
                ref={input}
                type="file"
                accept=".tar.bz2"
                hidden
                aria-label={t('importArchive')}
                disabled={busy}
                onChange={() => selectFile(input.current?.files?.[0])}
            />
            <Flex gap={2} alignItems="center" wrap="wrap">
                <Button size="l" view="flat-secondary" disabled={busy} onClick={() => input.current?.click()}>
                    <Icon data={FileIcon} size={20} />
                    {t('importArchive')}
                </Button>
                {file && <Text className={styles.file}>{file.name}</Text>}
            </Flex>
            <Flex direction="column" gap={2}>
                <Text>{t('importName')}</Text>
                <TextInput
                    value={title}
                    onUpdate={setTitle}
                    size="l"
                    disabled={busy}
                    controlProps={{'aria-label': t('importName'), maxLength: 80}}
                />
            </Flex>
            <Flex direction="column" gap={2}>
                <Text>{t('importLanguage')}</Text>
                <TextInput
                    value={language}
                    onUpdate={setLanguage}
                    size="l"
                    disabled={busy}
                    controlProps={{'aria-label': t('importLanguage'), maxLength: 10}}
                />
            </Flex>
            <Flex direction="column" gap={2}>
                <Text>{t('importLicense')}</Text>
                <TextInput
                    value={license}
                    onUpdate={setLicense}
                    size="l"
                    disabled={busy}
                    controlProps={{'aria-label': t('importLicense'), maxLength: 512}}
                />
            </Flex>
            {busy && (
                <Flex direction="column" gap={2} role="status">
                    <Text>{t(phase === 'uploading' ? 'importUploading' : 'importInstalling')}</Text>
                    <Box
                        role="progressbar"
                        aria-label={t('importProgress')}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={progress}
                    >
                        <Progress value={progress} />
                    </Box>
                </Flex>
            )}
            {error && (
                <Text color="danger" role="alert">
                    {error}
                </Text>
            )}
            {phase === 'ready' && (
                <Flex gap={2} alignItems="center" role="status">
                    <Icon data={Check} size={18} />
                    <Text>{t('importReady')}</Text>
                </Flex>
            )}
            <Flex gap={2} wrap="wrap">
                <Button
                    size="l"
                    view="flat-secondary"
                    disabled={busy || !file || !title.trim()}
                    onClick={() => void submit()}
                >
                    <Icon data={ArrowUpFromLine} size={20} />
                    {t('importSubmit')}
                </Button>
                {busy && (
                    <Button size="l" view="flat-secondary" onClick={() => pending.current?.abort()}>
                        <Icon data={Xmark} size={20} />
                        {t('cancel')}
                    </Button>
                )}
            </Flex>
        </Flex>
    );
}
