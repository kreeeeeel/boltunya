import {Button, Flex, Icon, Text} from '@gravity-ui/uikit';
import {Archive, Books} from '@gravity-ui/icons';
import {useTranslations} from 'next-intl';

import type {ChatHistoryActionsProps} from '@/modules/workspace/ChatHistoryActions/ChatHistoryActions.types';
import {useMemoryDocument} from '@/modules/memory/model/useMemoryDocument';
import {ContextCollectionStatus} from '@/modules/memory/ContextCollectionStatus/ContextCollectionStatus';

export function ChatHistoryActions({chatId, onCollectContext, onDeleted, disabled}: ChatHistoryActionsProps) {
    const t = useTranslations();
    const memory = useMemoryDocument(chatId);
    const progress = memory.document?.collection;

    return (
        <Flex direction="column" gap={2} qa="chat-history-actions">
            {progress?.active ? (
                <ContextCollectionStatus
                    progress={progress}
                    busy={memory.busy}
                    onCancel={() => void memory.cancel()}
                    onDetails={onCollectContext}
                />
            ) : (
                <Button size="l" width="max" view="outlined" disabled={disabled} onClick={onCollectContext}>
                    <Button.Icon side="left">
                        <Icon data={Books} size={18} />
                    </Button.Icon>
                    {t('context.title')}
                </Button>
            )}
            {memory.error && (
                <Text color="danger" role="alert">
                    {memory.error}
                </Text>
            )}
            <Button size="l" width="max" view="outlined" disabled={disabled} onClick={onDeleted}>
                <Button.Icon side="left">
                    <Icon data={Archive} size={18} />
                </Button.Icon>
                {t('deleted.title')}
            </Button>
        </Flex>
    );
}
