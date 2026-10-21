export interface VoiceComposeDialogProps {
    readonly chatId: string;
    readonly defaultModelId: string;
    readonly defaultSpeed?: number;
    readonly onClose: () => void;
    readonly onSent: () => Promise<void>;
    readonly onSettings?: () => void;
}
