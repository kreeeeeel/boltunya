import {
    ChatsApi,
    Configuration,
    SystemApi,
    type ConfigurationParameters,
} from "@aigram/api-client";

const settings: ConfigurationParameters = {
    basePath: "http://127.0.0.1:9000",
    credentials: "include",
    headers: { Cookie: "tiho=synthetic-session" },
};
const configuration = new Configuration(settings);
const chats = new ChatsApi(configuration);
const system = new SystemApi(configuration);

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
        text: { text: "Hello" },
    });
}

export async function readState() {
    return system.state();
}
