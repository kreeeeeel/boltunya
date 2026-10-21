/** Retains unchanged JSON branches so polling does not invalidate every chat and media component. */
export function shareSnapshot<T>(previous: T, current: T): T {
    if (Object.is(previous, current)) {
        return previous;
    }
    if (Array.isArray(previous) && Array.isArray(current)) {
        const byId = new Map(
            previous.flatMap((item) =>
                item && typeof item === 'object' && typeof item.id === 'string'
                    ? [[item.id, item] as const]
                    : [],
            ),
        );
        const merged = current.map((item, index) =>
            shareSnapshot(
                item && typeof item === 'object' && typeof item.id === 'string'
                    ? byId.get(item.id)
                    : previous[index],
                item,
            ),
        );
        return (
            previous.length === merged.length && merged.every((item, index) => item === previous[index])
                ? previous
                : merged
        ) as T;
    }
    if (
        previous &&
        current &&
        typeof previous === 'object' &&
        typeof current === 'object' &&
        !Array.isArray(previous) &&
        !Array.isArray(current)
    ) {
        const old = previous as Record<string, unknown>;
        const next = current as Record<string, unknown>;
        const entries = Object.entries(next).map(
            ([key, value]) => [key, shareSnapshot(old[key], value)] as const,
        );
        if (
            Object.keys(old).length === entries.length &&
            entries.every(([key, value]) => Object.hasOwn(old, key) && value === old[key])
        ) {
            return previous;
        }
        return Object.fromEntries(entries) as T;
    }
    return current;
}
