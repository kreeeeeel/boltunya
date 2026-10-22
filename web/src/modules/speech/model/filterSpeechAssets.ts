import type {SpeechAsset} from '@aigram/api-client';

export function filterSpeechAssets(
    assets: SpeechAsset[],
    kind: SpeechAsset['kind'] | undefined,
    query: string,
    language: string,
    locale: string,
    installedOnly = false,
): SpeechAsset[] {
    const search = query.trim().toLocaleLowerCase(locale);
    return assets.filter(
        (asset) =>
            (!kind || asset.kind === kind) &&
            (!installedOnly || asset.installed) &&
            (!language || asset.languages.includes(language)) &&
            (!search ||
                [asset.title, asset.id, ...asset.languages].some((value) =>
                    value.toLocaleLowerCase(locale).includes(search),
                )),
    );
}
