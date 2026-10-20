import type {Activity, ActivityDetails} from '@/shared/api/client';

export const testActivity: Activity[] = [
    {
        id: 'running',
        chatId: '41',
        title: 'Аня Смирнова',
        status: 'generating',
        detail: 'deepseek-r1:1.5b',
        date: 1792152004,
        durationMs: 8200,
        phase: 'reply',
        thinking: 'Собеседник спрашивает о встрече. Уточню время, отвечу коротко и без лишних подробностей.',
        preview: 'Во сколько завтра встречаемся?',
        attempts: 1,
        feedbackAvailable: false,
        feedbackRating: null,
    },
    {
        id: 'queued',
        chatId: '42',
        title: 'Максим Волков',
        status: 'queued',
        detail: 'qwen3:1.7b',
        date: 1792152003,
        durationMs: 0,
        phase: 'queued',
        queuePosition: 1,
        preview: 'Как прошёл день?',
        feedbackAvailable: false,
        feedbackRating: null,
    },
    {
        id: 'sent',
        chatId: '43',
        title: 'Лиза',
        status: 'sent',
        detail: 'gemma3:4b',
        date: 1792152002,
        durationMs: 3400,
        phase: 'sent',
        inputTokens: 412,
        outputTokens: 28,
        attempts: 1,
        preview: 'Давай в шесть, мне удобно. Тебе норм?',
        feedbackAvailable: true,
        feedbackRating: null,
    },
    {
        id: 'error',
        chatId: '44',
        title: 'Команда проекта',
        status: 'error',
        detail: 'Модель не ответила. Проверьте, запущен ли Ollama.',
        date: 1792152001,
        durationMs: 1500,
        phase: 'error',
        attempts: 1,
        feedbackAvailable: false,
        feedbackRating: null,
    },
];

export function activityDetails(entry: Activity): ActivityDetails {
    return {
        entry,
        feedback: null,
        trigger: 'Во сколько завтра встречаемся?',
        response: entry.status === 'sent' ? 'Давай в шесть, мне удобно. Тебе норм?' : '',
        steps: [
            {phase: 'queued', date: 1792152000000},
            {phase: 'context', date: 1792152001000},
            {phase: 'reply', date: 1792152002000},
        ],
        attempts:
            entry.status === 'queued'
                ? []
                : [
                      {
                          number: 1,
                          purpose: 'reply',
                          model: 'deepseek-r1:1.5b',
                          startedAt: 1792152002000,
                          messages: [
                              {role: 'system', text: 'Отвечай коротко, учитывай историю разговора.'},
                              {role: 'user', text: 'Во сколько завтра встречаемся?'},
                          ],
                          options: '{"model":"deepseek-r1:1.5b","temperature":0.6,"num_predict":2176}',
                          thinking: 'Уточню время встречи и спрошу, удобно ли собеседнику.',
                          response: 'Давай в шесть, мне удобно. Тебе норм?',
                          durationMs: 3400,
                          inputTokens: 412,
                          outputTokens: 28,
                          tokensPerSecond: 18.2,
                          loadDurationMs: 300,
                          promptDurationMs: 400,
                          finishReason: 'stop',
                          complete: true,
                      },
                  ],
    };
}
