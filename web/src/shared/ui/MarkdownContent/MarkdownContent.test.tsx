import {render, screen} from '@testing-library/react';
import {expect, it} from 'vitest';

import {MarkdownContent} from '@/shared/ui/MarkdownContent/MarkdownContent';

it('renders memory formatting as semantic content and opens safe links externally', () => {
    const view = render(
        <MarkdownContent>
            {'## Факты\n\nЛюбит **чай**.\n\n- Короткие ответы\n\n[Источник](https://example.com)'}
        </MarkdownContent>,
    );
    expect(screen.getByRole('heading', {name: 'Факты'})).toBeInTheDocument();
    expect(view.container.querySelector('strong')).toHaveTextContent('чай');
    expect(screen.getByRole('listitem')).toHaveTextContent('Короткие ответы');
    expect(screen.getByRole('link', {name: 'Источник'})).toHaveAttribute('rel', 'noopener noreferrer');
    expect(view.container).not.toHaveTextContent('**');
});

it('does not execute HTML or unsafe Markdown links from generated text', () => {
    const view = render(
        <MarkdownContent>{'<script>alert(1)</script>\n\n[Нельзя](javascript:alert)'}</MarkdownContent>,
    );
    expect(view.container.querySelector('script')).toBeNull();
    expect(view.container.querySelector('[href^="javascript:"]')).toBeNull();
});

it('does not request remote images embedded in generated memory', () => {
    const view = render(
        <MarkdownContent>{'![Иллюстрация](https://example.com/tracker.png)'}</MarkdownContent>,
    );
    expect(view.container.querySelector('img')).toBeNull();
    expect(screen.getByText('Иллюстрация')).toBeVisible();
});
