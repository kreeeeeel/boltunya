export class DraftPreparationError extends Error {
    constructor(
        message: string,
        readonly status: 'cancelled' | 'error',
    ) {
        super(message);
        this.name = 'DraftPreparationError';
    }
}
