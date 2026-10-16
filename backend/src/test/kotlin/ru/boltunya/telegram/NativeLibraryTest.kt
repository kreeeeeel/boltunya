package ru.boltunya.telegram

import it.tdlight.Init
import it.tdlight.jni.TdApi
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class NativeLibraryTest {
    @Test
    fun `loads the Windows TDLight native library without credentials`() {
        Init.init()
        assertNotNull(TdApi.GetMe())
    }
}
