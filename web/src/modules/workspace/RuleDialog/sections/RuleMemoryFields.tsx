import {Flex, Switch} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {ChatHistoryActions} from '@/modules/workspace/ChatHistoryActions';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';
import {SettingsSlider} from '@/shared/ui/SettingsSlider';

export function RuleMemoryFields({
    value,
    onUpdate,
    chatId,
    disabled,
    onCollectContext,
    onDeleted,
}: RuleFieldsProps & {
    readonly chatId: string;
    readonly disabled: boolean;
    readonly onCollectContext: () => void;
    readonly onDeleted: () => void;
}) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={5}>
            <Switch
                checked={value.useMemory ?? true}
                onUpdate={(useMemory) => onUpdate({...value, useMemory})}
            >
                {t('memory.useChat')}
            </Switch>
            <SettingsSlider
                label={t('depth')}
                displayValue={t('messagesCount', {count: value.contextMessages})}
                hint={t('contextNote')}
                value={value.contextMessages}
                min={4}
                max={60}
                onUpdate={(contextMessages) => onUpdate({...value, contextMessages})}
            />
            <ChatHistoryActions
                chatId={chatId}
                disabled={disabled}
                onCollectContext={onCollectContext}
                onDeleted={onDeleted}
            />
        </Flex>
    );
}
