package ru.boltunya.ai

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

internal class InferenceResource {
    val lock = ReentrantLock(true)
    val foreground = AtomicInteger()
    val background = AtomicReference<Thread?>()
}
