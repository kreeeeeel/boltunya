import type {ReactNode} from 'react';

export interface SettingsSliderProps {
    readonly label: string;
    readonly value: number;
    readonly displayValue: ReactNode;
    readonly onUpdate: (value: number) => void;
    readonly min: number;
    readonly max: number;
    readonly step?: number;
    readonly disabled?: boolean;
    readonly hint?: ReactNode;
    readonly qa?: string;
}
