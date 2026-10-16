package ru.boltunya

import com.google.inject.Guice
import ru.boltunya.application.ApplicationRuntime
import ru.boltunya.configuration.ApplicationConfiguration
import ru.boltunya.configuration.ApplicationModule
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val directory = Path.of(
        System.getenv("BOLTUNYA_DATA_DIR") ?: System.getenv("TIHO_DATA_DIR") ?: "${System.getenv("LOCALAPPDATA")}/Tiho",
    )
    Guice.createInjector(
        ApplicationModule(ApplicationConfiguration(directory, args.contains("--startup"))),
    ).getInstance(ApplicationRuntime::class.java).use { application ->
        if (!application.start()) return
        Runtime.getRuntime().addShutdownHook(Thread { application.close() })
        CountDownLatch(1).await()
    }
}
