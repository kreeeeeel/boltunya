import {Flex, Slider, Text} from '@gravity-ui/uikit';
import type {SettingsSliderProps} from '@/shared/ui/SettingsSlider/SettingsSlider.types';
import styles from '@/shared/ui/SettingsSlider/SettingsSlider.module.css';

export function SettingsSlider({
    label,
    value,
    displayValue,
    onUpdate,
    min,
    max,
    step,
    disabled,
    hint,
    qa,
}: SettingsSliderProps) {
    return (
        <Flex direction="column" className={styles.root} qa={qa ?? 'settings-slider'}>
            <Flex justifyContent="space-between" alignItems="center" gap={3} className={styles.heading}>
                <Text className={styles.label}>{label}</Text>
                <Text color="secondary" className={styles.value}>
                    {displayValue}
                </Text>
            </Flex>
            <Slider
                className={styles.control}
                tooltipDisplay="off"
                aria-label={label}
                value={value}
                min={min}
                max={max}
                step={step}
                disabled={disabled}
                onUpdate={onUpdate}
            />
            {hint && <Text color="secondary">{hint}</Text>}
        </Flex>
    );
}
