import {
    ChatsApi,
    Configuration,
    ModelsApi,
    SystemApi,
    type ConfigurationParameters,
    type Ok,
    type MessageEditOptions,
    type MessageEditRequest,
    SettingsApi,
    type Reply,
} from "@aigram/api-client";

const settings: ConfigurationParameters = {
    basePath: "http://127.0.0.1:9000",
    credentials: "include",
    headers: { Cookie: "tiho=synthetic-session" },
};
const configuration = new Configuration(settings);
const chats = new ChatsApi(configuration);
const system = new SystemApi(configuration);
const models = new ModelsApi(configuration);

type EngineModel = Awaited<
    ReturnType<SystemApi["state"]>
>["engine"]["models"][number];
type TypedEngineModel = 0 extends 1 & EngineModel ? never : EngineModel;

export function modelId(model: TypedEngineModel): string {
    return model.id;
}

export async function readHistory(): Promise<string[]> {
    const messages = await chats.history({
        chatId: "-1009007199254740993555",
        before: "9007199254740993999",
    });
    return messages.map((message) => {
        const id: string = message.id;
        return id;
    });
}

export async function sendText() {
    return chats.send({
        chatId: "-1009007199254740993555",
        text: { text: "Hello", entities: [{ offset: 0, length: 5, type: "bold" }] },
    });
}

export async function configureFormattedIdleMessage(): Promise<Ok> {
    const preferences = new SettingsApi(configuration);
    await preferences.settings({ settingsUpdate: { formatReplies: true, replyProcessingMode: "each" } });
    return chats.rule({
        chatId: "42",
        chatRule: {
            enabled: false,
            replyInterruptionMode: "finish",
            proactiveMode: "enabled",
            proactiveMessageMode: "text",
            proactiveText: "How are you?",
            proactiveIntervalMinutes: 60,
        },
    });
}

export function formattedReply(reply: Reply): string[] {
    return (reply.entities ?? []).map((entity) => entity.type);
}

export async function readState() {
    return system.state();
}

export function canRestartEngine(
    state: Awaited<ReturnType<SystemApi["state"]>>,
): boolean {
    return state.engine.canRestart ?? false;
}

export async function restartLocalEngine(): Promise<Ok> {
    return models.restartEngine();
}

export async function editCaption(): Promise<string> {
    const chatId = "-1009007199254740993555";
    const messageId = "9007199254740993999";
    const original: MessageEditOptions = await chats.messageEditOptions({chatId, messageId});
    const messageEditRequest: MessageEditRequest = {
        text: `${original.text}\nAn exact new line`,
        entities: original.entities,
        revision: original.revision,
    };
    const updated = await chats.editMessage({chatId, messageId, messageEditRequest});
    return updated.text;
}
