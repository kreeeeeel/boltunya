package ru.aigram.telegram

import it.tdlight.jni.TdApi
import jakarta.inject.Inject
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class TelegramCache @Inject constructor() {
    val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    val users = ConcurrentHashMap<Long, TdApi.User>()
    val basicGroups = ConcurrentHashMap<Long, TdApi.BasicGroup>()
    val supergroups = ConcurrentHashMap<Long, TdApi.Supergroup>()

    @Volatile var ownId: Long = 0

    @Volatile var lightBackground: TdApi.Background? = null

    @Volatile var darkBackground: TdApi.Background? = null
    val startedAt: Long = Instant.now().epochSecond

    fun updateChat(id: Long, update: (TdApi.Chat) -> Unit) {
        chats.computeIfPresent(id) { _, chat ->
            update(chat)
            chat
        }
    }

    fun updatePosition(id: Long, position: TdApi.ChatPosition) {
        updateChat(id) { chat ->
            val others = chat.positions.orEmpty().filter { it.list.constructor != position.list.constructor }
            chat.positions = (if (position.order == 0L) others else others + position).toTypedArray()
        }
    }
    fun clear() {
        chats.clear()
        users.clear()
        basicGroups.clear()
        supergroups.clear()
        ownId = 0
        lightBackground = null
        darkBackground = null
    }
}
