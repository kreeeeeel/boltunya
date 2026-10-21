package ru.aigram.automation.activity

import ru.aigram.domain.chat.ChatSummary
import ru.aigram.domain.events.ActivityEntry

/** Archived media URLs belong to a previous TDLib registry and cannot identify a chat photo. */
internal fun ActivityEntry.withCurrentAvatar(chat: ChatSummary?): ActivityEntry = copy(
    avatarUrl = chat?.takeIf { it.id == chatId }?.avatarUrl.orEmpty(),
)
