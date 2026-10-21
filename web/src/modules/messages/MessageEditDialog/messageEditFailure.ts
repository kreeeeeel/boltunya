import {ApiRequestError} from '@/shared/api/ApiRequestError';

export function messageEditFailure(cause: unknown, phase: 'load' | 'save'): string {
    if (cause instanceof ApiRequestError) {
        if (cause.code === 'MESSAGE_EDIT_CONFLICT') {
            return 'changedElsewhere';
        }
        if (cause.code === 'MESSAGE_EDIT_NOT_ALLOWED' || cause.status === 404) {
            return 'unavailable';
        }
        if (cause.code === 'MESSAGE_EDIT_TOO_LONG') {
            return 'tooLong';
        }
        if (cause.code === 'INVALID_MESSAGE_EDIT') {
            return 'invalidFormat';
        }
        if (cause.status === 429) {
            return 'rateLimited';
        }
    }
    return phase === 'load' ? 'loadFailed' : 'saveFailed';
}
