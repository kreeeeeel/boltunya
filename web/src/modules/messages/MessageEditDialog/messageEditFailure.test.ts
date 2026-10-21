import {expect, it} from 'vitest';
import {ApiRequestError} from '@/shared/api/ApiRequestError';
import {messageEditFailure} from '@/modules/messages/MessageEditDialog/messageEditFailure';

it('distinguishes permission changes from revision conflicts despite their shared HTTP status', () => {
    expect(
        messageEditFailure(
            new ApiRequestError('Private server detail', 409, 'MESSAGE_EDIT_CONFLICT'),
            'save',
        ),
    ).toBe('changedElsewhere');
    expect(
        messageEditFailure(
            new ApiRequestError('Private server detail', 409, 'MESSAGE_EDIT_NOT_ALLOWED'),
            'save',
        ),
    ).toBe('unavailable');
    expect(
        messageEditFailure(
            new ApiRequestError('Private server detail', 400, 'MESSAGE_EDIT_TOO_LONG'),
            'save',
        ),
    ).toBe('tooLong');
    expect(
        messageEditFailure(new ApiRequestError('Private server detail', 429, 'TELEGRAM_RATE_LIMIT'), 'save'),
    ).toBe('rateLimited');
    expect(messageEditFailure(new Error('Private server detail'), 'load')).toBe('loadFailed');
    expect(messageEditFailure(new Error('Private server detail'), 'save')).toBe('saveFailed');
});
