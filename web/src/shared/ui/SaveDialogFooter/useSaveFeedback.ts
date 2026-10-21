import {useEffect, useState} from 'react';

const SAVED_FEEDBACK_MS = 3000;

export function useSaveFeedback<T>(value: T) {
    const [savedValue, setSavedValue] = useState<T | null>(null);

    useEffect(() => {
        if (savedValue === null) {
            return;
        }
        const timeout = window.setTimeout(() => setSavedValue(null), SAVED_FEEDBACK_MS);
        return () => window.clearTimeout(timeout);
    }, [savedValue]);

    return {
        saved: savedValue !== null && savedValue === value,
        markSaved: () => setSavedValue(value),
        markValueSaved: (nextValue: T) => setSavedValue(nextValue),
        clearSaved: () => setSavedValue(null),
    };
}
