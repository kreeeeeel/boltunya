import type {SpeechComputeStatus} from '@aigram/api-client';

export const speechCompute: SpeechComputeStatus = {
    selectedDevice: 'cpu',
    runtimeInstalled: true,
    cudaAvailable: true,
    gpuName: 'Synthetic NVIDIA GPU',
    gpuMemoryBytes: 8 * 1024 ** 3,
    torchVersion: '2.11.0+cu128',
    cudaVersion: '12.8',
    reason: 'ready',
};
