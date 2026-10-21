import type * as Contract from '@aigram/api-client';
export const computeDevices: Contract.ComputeDeviceStatus = {
    configured: 'auto',
    active: 'auto',
    managed: true,
    selectionSupported: true,
    restartRequired: false,
    autoUsesEnvironment: true,
    nvidiaSelectionOnly: true,
    limitation: '',
    devices: [
        {id: 'auto', name: 'Automatic', kind: 'auto', memoryBytes: 0, selectable: true, reason: ''},
        {id: 'cpu', name: 'CPU', kind: 'cpu', memoryBytes: 0, selectable: true, reason: ''},
        {
            id: 'GPU-11111111-2222-3333-4444-555555555555',
            name: 'NVIDIA GeForce RTX 4070',
            kind: 'nvidia',
            memoryBytes: 12 * 1024 ** 3,
            selectable: true,
            reason: '',
        },
    ],
};
