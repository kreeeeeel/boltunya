import {Flex, NumberInput, Select} from '@gravity-ui/uikit';
import {useState} from 'react';
import {useTranslations} from 'next-intl';

import styles from '@/modules/settings/ProactiveInterval/ProactiveInterval.module.css';

const MAX_INTERVAL_MINUTES = 43200;

export function ProactiveInterval({
    value,
    disabled = false,
    onUpdate,
}: {
    readonly value: number;
    readonly disabled?: boolean;
    readonly onUpdate: (minutes: number) => void;
}) {
    const t = useTranslations('proactive');
    const [unit, setUnit] = useState(value >= 60 && value % 60 === 0 ? 'hours' : 'minutes');
    const multiplier = unit === 'hours' ? 60 : 1;

    return (
        <Flex gap={2} className={styles.root}>
            <NumberInput
                size="l"
                value={value / multiplier}
                min={unit === 'hours' ? 1 : 5}
                max={MAX_INTERVAL_MINUTES / multiplier}
                step={1}
                disabled={disabled}
                className={styles.value}
                onUpdate={(next) => {
                    if (next !== null && next > 0) {
                        onUpdate(Math.round(next) * multiplier);
                    }
                }}
                controlProps={{'aria-label': t('interval')}}
            />
            <Select
                size="l"
                disabled={disabled}
                value={[unit]}
                options={['minutes', 'hours'].map((value) => ({value, content: t(value)}))}
                className={styles.unit}
                aria-label={t('unit')}
                onUpdate={([next]) => {
                    if (next === 'minutes' || next === 'hours') {
                        const amount = Math.max(next === 'hours' ? 1 : 5, Math.round(value / multiplier));
                        setUnit(next);
                        onUpdate(Math.min(MAX_INTERVAL_MINUTES, amount * (next === 'hours' ? 60 : 1)));
                    }
                }}
            />
        </Flex>
    );
}
