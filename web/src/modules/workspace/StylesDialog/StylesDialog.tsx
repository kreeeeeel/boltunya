import {Box, Button, Dialog, Flex, Icon, Text, TextArea, TextInput} from '@gravity-ui/uikit';
import {Plus, TrashBin} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import {api, dataOrThrow, errorMessage} from '@/shared/api/client';
import type {AppState} from '@/shared/api/client';
import {EmojiPicker} from '@/shared/ui/EmojiPicker/EmojiPicker';

type WritingStyle = NonNullable<AppState['settings']['customStyles']>[number];
export function StylesDialog({
    styles,
    onClose,
    onRefresh,
}: {
    readonly styles: WritingStyle[];
    readonly onClose: () => void;
    readonly onRefresh: () => Promise<void>;
}) {
    const t = useTranslations();
    const [items, setItems] = useState(styles);
    const [selected, setSelected] = useState(styles[0]?.id ?? '');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const active = items.find((item) => item.id === selected);
    function update(values: Partial<WritingStyle>) {
        setItems(items.map((item) => (item.id === selected ? {...item, ...values} : item)));
    }
    async function save() {
        if (items.some((item) => !item.name.trim() || !item.emoji.trim() || !item.instructions.trim())) {
            setError(t('styleRequired'));
            return;
        }
        setBusy(true);
        try {
            dataOrThrow(await api.PATCH('/api/settings', {body: {customStyles: items}}));
            await onRefresh();
            onClose();
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
            maxWidth="s"
            fullWidth
            onClose={onClose}
            aria-label={t('customStyles')}
        >
            <Dialog.Header caption={t('customStyles')} />
            <Dialog.Body>
                <Flex direction="column" gap={4}>
                    <Text color="secondary">{t('customStylesHint')}</Text>
                    <Flex wrap gap={2}>
                        {items.map((item) => (
                            <Button
                                key={item.id}
                                view={item.id === selected ? 'outlined-info' : 'outlined'}
                                onClick={() => setSelected(item.id)}
                            >
                                {item.emoji} {item.name || t('newStyle')}
                            </Button>
                        ))}
                    </Flex>
                    <Button
                        disabled={items.length >= 30}
                        onClick={() => {
                            const id = crypto.randomUUID();
                            setItems([...items, {id, name: '', emoji: '✍️', instructions: ''}]);
                            setSelected(id);
                        }}
                    >
                        <Icon data={Plus} size={16} />
                        {t('createStyle')}
                    </Button>
                    {active && (
                        <Flex direction="column" gap={4}>
                            <Box>
                                <Text as="label" htmlFor="style-name">
                                    {t('styleName')}
                                </Text>
                                <TextInput
                                    id="style-name"
                                    value={active.name}
                                    onUpdate={(name) => update({name})}
                                    controlProps={{id: 'style-name', maxLength: 40}}
                                    size="l"
                                    startContent={
                                        <EmojiPicker
                                            value={active.emoji}
                                            onUpdate={(emoji) => update({emoji})}
                                        />
                                    }
                                />
                            </Box>
                            <Box>
                                <Text as="label" htmlFor="style-instructions">
                                    {t('styleInstructions')}
                                </Text>
                                <TextArea
                                    id="style-instructions"
                                    value={active.instructions}
                                    onUpdate={(instructions) => update({instructions})}
                                    controlProps={{id: 'style-instructions', maxLength: 4000}}
                                    minRows={5}
                                />
                            </Box>
                            <Button
                                view="flat-danger"
                                onClick={() => {
                                    const next = items.filter((item) => item.id !== selected);
                                    setItems(next);
                                    setSelected(next[0]?.id ?? '');
                                }}
                            >
                                <Icon data={TrashBin} size={16} />
                                {t('deleteStyle')}
                            </Button>
                        </Flex>
                    )}
                    {error && <Text color="danger">{error}</Text>}
                </Flex>
            </Dialog.Body>
            <Dialog.Footer
                textButtonApply={t('save')}
                textButtonCancel={t('cancel')}
                onClickButtonApply={() => void save()}
                onClickButtonCancel={onClose}
                loading={busy}
            />
        </Dialog>
    );
}
