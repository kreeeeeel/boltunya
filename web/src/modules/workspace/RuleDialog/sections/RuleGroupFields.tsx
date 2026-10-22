import {Flex, Select, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import type {ChatRule} from '@/shared/api/client';
import type {RuleFieldsProps} from '@/modules/workspace/RuleDialog/model/RuleFieldsProps';

export function RuleGroupFields({value, onUpdate}: RuleFieldsProps) {
    const t = useTranslations();
    return (
        <Flex direction="column" gap={2}>
            <Text>{t('groupReplies')}</Text>
            <Select
                size="l"
                width="max"
                value={[value.groupReplies ?? 'inherit']}
                options={['inherit', 'disabled', 'mentions', 'all'].map((mode) => ({
                    value: mode,
                    content: t('groupMode.' + mode),
                }))}
                onUpdate={([groupReplies]) =>
                    onUpdate({...value, groupReplies: groupReplies as ChatRule['groupReplies']})
                }
                aria-label={t('groupReplies')}
            />
        </Flex>
    );
}
