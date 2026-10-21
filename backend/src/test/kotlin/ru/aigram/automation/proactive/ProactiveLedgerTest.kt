package ru.aigram.automation.proactive

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.aigram.domain.chat.ChatMessage
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProactiveLedgerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `successful initiative survives restart and only a newer real incoming message unlocks it`() {
        val ledger = ProactiveLedger(directory)
        val reservation = ledger.reserve("owner", "42", "10", 100)
        ledger.prepareDelivery("owner", "42", reservation)
        val restored = ProactiveLedger(directory)
        assertTrue(restored.get("owner", "42")?.awaitingResponse == true)
        assertNull(restored.get("other-owner", "42"))
        listOf(
            ChatMessage("9", "42", "old", false, 0),
            ChatMessage("11", "42", "manual", true, 0),
            ChatMessage("12", "42", "AI", false, 0, automated = true),
            ChatMessage("13", "42", "deleted", false, 0, deleted = true),
        ).stream().forEach { restored.observe("owner", it) }
        assertTrue(restored.get("owner", "42")?.awaitingResponse == true)
        restored.observe("owner", ChatMessage("14", "42", "reply", false, 0))
        assertFalse(ProactiveLedger(directory).get("owner", "42")?.awaitingResponse == true)
        restored.observe("owner", ChatMessage("12", "42", "late update", false, 0))
        assertEquals("14", restored.get("owner", "42")?.lastIncomingId)
    }

    @Test
    fun `incoming response or a newer reservation rejects stale delivery`() {
        val ledger = ProactiveLedger(directory)
        val reservation = ledger.reserve("owner", "42", "10", 100)
        ledger.observe("owner", ChatMessage("11", "42", "response", false, 0))
        ledger.prepareDelivery("owner", "42", reservation)
        assertFalse(ledger.get("owner", "42")?.awaitingResponse == true)
        val newer = ledger.reserve("owner", "42", "12", 200)
        ledger.prepareDelivery("owner", "42", reservation)
        assertEquals(newer, ledger.get("owner", "42"))
    }
}
