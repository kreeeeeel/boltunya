import {Configuration} from '@aigram/api-client';
import {ApiRequestError} from '@/shared/api/ApiRequestError';

export const apiConfiguration = new Configuration({
    basePath: '',
    credentials: 'same-origin',
    middleware: [
        {
            async post({response}) {
                if (!response.ok) {
                    let message = `HTTP ${response.status}`;
                    let code = '';
                    try {
                        const body: unknown = await response.json();
                        if (body && typeof body === 'object') {
                            if ('message' in body && typeof body.message === 'string') {
                                message = body.message;
                            }
                            if ('code' in body && typeof body.code === 'string') {
                                code = body.code;
                            }
                        }
                    } catch {
                        // Invalid or empty error bodies retain their HTTP status instead of showing HTML.
                    }
                    throw new ApiRequestError(message, response.status, code);
                }
                return response;
            },
            async onError({error}) {
                throw error;
            },
        },
    ],
});
