package com.messoff.pulso.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Copia de la agenda en el móvil: permite abrir la app, los avisos y el widget sin red. */
@Serializable
data class Snapshot(
    val user: User,
    val events: List<CalEvent> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val teams: List<Team> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
)

/** Un cambio hecho sin conexión, pendiente de enviar al servidor. */
@Serializable
data class PendingOp(val id: String, val method: String, val path: String, val body: String? = null)

class LocalStore(context: Context) {
    private val dir = context.filesDir
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }

    fun loadSnapshot(): Snapshot? = runCatching {
        json.decodeFromString(Snapshot.serializer(), File(dir, SNAPSHOT).readText())
    }.getOrNull()

    fun saveSnapshot(snapshot: Snapshot) {
        runCatching { write(SNAPSHOT, json.encodeToString(Snapshot.serializer(), snapshot)) }
    }

    fun loadQueue(): List<PendingOp> = runCatching {
        json.decodeFromString(ListSerializer(PendingOp.serializer()), File(dir, QUEUE).readText())
    }.getOrDefault(emptyList())

    fun saveQueue(queue: List<PendingOp>) {
        runCatching { write(QUEUE, json.encodeToString(ListSerializer(PendingOp.serializer()), queue)) }
    }

    fun clear() {
        File(dir, SNAPSHOT).delete()
        File(dir, QUEUE).delete()
    }

    private companion object {
        const val SNAPSHOT = "agenda.json"
        const val QUEUE = "pendientes.json"
    }
}
