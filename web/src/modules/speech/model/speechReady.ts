import type {SpeechAsset} from '@aigram/api-client';

export function speechReady(assets: SpeechAsset[], selected: string): boolean {
    const model = assets.find((asset) => asset.id === selected && asset.kind === 'synthesis');
    return (
        !!model?.installed &&
        model.dependencies.every((id) => assets.some((asset) => asset.id === id && asset.installed))
    );
}
