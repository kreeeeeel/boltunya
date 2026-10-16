package ru.boltunya.flows

import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import ru.boltunya.exception.impl.FlowLimitReachedException
import ru.boltunya.exception.impl.FlowStorageLimitReachedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val MAX_FLOWS = 50
private const val MAX_STORE_KEYS = 256

class FlowStore @Inject constructor(private val directory: Path, private val validation: FlowValidation) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val file = directory.resolve("flows.json")
    private val dataFile = directory.resolve("flow-data.json")

    @Volatile private var flows: List<AutomationFlow> = if (Files.exists(
            file,
        )
    ) {
        json.decodeFromString(Files.readString(file))
    } else {
        emptyList()
    }
    private var data: Map<String, Map<String, String>> = if (Files.exists(
            dataFile,
        )
    ) {
        json.decodeFromString(Files.readString(dataFile))
    } else {
        emptyMap()
    }

    fun list(): List<AutomationFlow> = flows

    @Synchronized
    fun save(flow: AutomationFlow) {
        validation.validate(flow)
        val next = flows.filterNot { it.id == flow.id } + flow
        if (next.size > MAX_FLOWS) throw FlowLimitReachedException()
        write(file, json.encodeToString(next))
        flows = next
    }

    @Synchronized
    fun delete(id: String) {
        val next = flows.filterNot { it.id == id }
        write(file, json.encodeToString(next))
        flows = next
    }

    @Synchronized
    fun values(flowId: String, chatId: String): Map<String, String> = data["$flowId/$chatId"].orEmpty().toMap()

    @Synchronized
    fun commit(flowId: String, chatId: String, values: Map<String, String>) {
        if (values.size >
            MAX_STORE_KEYS
        ) {
            throw FlowStorageLimitReachedException()
        }
        val next = data + ("$flowId/$chatId" to values)
        write(dataFile, json.encodeToString(next))
        data = next
    }

    private fun write(path: Path, text: String) {
        Files.createDirectories(directory)
        val temporary = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temporary, text)
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
