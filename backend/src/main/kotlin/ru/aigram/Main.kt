package ru.aigram

import org.springframework.boot.Banner
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import ru.aigram.application.ApplicationRuntime
import ru.aigram.configuration.AigramApplication
import ru.aigram.configuration.ApplicationConfiguration
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val directory = Path.of(
        System.getenv("AIGRAM_DATA_DIR") ?: System.getenv("BOLTUNYA_DATA_DIR") ?: System.getenv("TIHO_DATA_DIR")
            ?: "${System.getenv("LOCALAPPDATA")}/Aigram",
    )
    val configuration = ApplicationConfiguration(directory, args.contains("--startup"))
    val application = SpringApplication(AigramApplication::class.java)
    application.setWebApplicationType(WebApplicationType.NONE)
    application.setBannerMode(Banner.Mode.OFF)
    application.setLogStartupInfo(false)
    application.addInitializers({ context ->
        context.beanFactory.registerSingleton("applicationConfiguration", configuration)
    })

    application.run(*args).use { context ->
        val runtime = context.getBean(ApplicationRuntime::class.java)
        if (!runtime.start()) {
            return
        }

        CountDownLatch(1).await()
    }
}
