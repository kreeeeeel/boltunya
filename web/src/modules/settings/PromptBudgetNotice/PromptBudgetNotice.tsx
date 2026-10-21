import {Flex, Label, Skeleton, Text} from '@gravity-ui/uikit';
import {useLocale, useTranslations} from 'next-intl';
import {usePromptBudget} from '@/modules/settings/model/usePromptBudget';
import type {PromptBudgetRequest} from '@/modules/settings/model/usePromptBudget';
import styles from '@/modules/settings/PromptBudgetNotice/PromptBudgetNotice.module.css';

export function PromptBudgetNotice({
    request,
    warningsOnly = false,
}: {
    readonly request: PromptBudgetRequest;
    readonly warningsOnly?: boolean;
}) {
    const t = useTranslations('promptBudget');
    const locale = useLocale();
    const budget = usePromptBudget(request);
    if (warningsOnly && ((!budget.report && !budget.error) || budget.report?.fits)) {
        return null;
    }
    const number = (value: number) => new Intl.NumberFormat(locale).format(value);
    return (
        <Flex direction="column" gap={3} className={styles.root} qa="prompt-budget">
            <Flex gap={2} alignItems="center" justifyContent="space-between" wrap>
                <Text variant="subheader-2">{t('title')}</Text>
                {budget.report && (
                    <Label theme={budget.report.fits ? 'normal' : 'danger'}>
                        {t(budget.report.fits ? 'estimated' : 'overLimit')}
                    </Label>
                )}
            </Flex>
            {budget.report ? (
                <>
                    {budget.report.automaticContext && <Text color="secondary">{t('automatic')}</Text>}
                    <Flex className={styles.metrics} gap={3} wrap>
                        {(
                            [
                                'contextWindow',
                                'systemTokens',
                                'reservedOutputTokens',
                                'remainingTokens',
                            ] as const
                        ).map((key) => (
                            <Flex key={key} direction="column" gap={1}>
                                <Text color="secondary">{t(key)}</Text>
                                <Text className={styles.value}>
                                    {number(Math.max(0, budget.report?.[key] ?? 0))}
                                </Text>
                            </Flex>
                        ))}
                    </Flex>
                    <Text color={budget.report.fits ? 'secondary' : 'danger'}>
                        {t(budget.report.fits ? 'hint' : 'tooLong')}
                    </Text>
                </>
            ) : budget.error ? (
                <Text color="danger" role="alert">
                    {budget.error}
                </Text>
            ) : (
                <Skeleton height={54} />
            )}
        </Flex>
    );
}
