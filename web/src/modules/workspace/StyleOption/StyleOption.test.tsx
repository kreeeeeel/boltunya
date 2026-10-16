import {render, screen} from '@testing-library/react';
import {describe, expect, it} from 'vitest';

import {StyleOption} from '@/modules/workspace/StyleOption/StyleOption';

describe('Communication style option', () => {
    it('keeps the emoji decorative and exposes the full style name', () => {
        render(<StyleOption emoji="🤬" name="Быдланский" />);
        expect(screen.getByText('Быдланский')).toBeVisible();
        expect(screen.getByText('🤬')).toHaveAttribute('aria-hidden', 'true');
    });
});
