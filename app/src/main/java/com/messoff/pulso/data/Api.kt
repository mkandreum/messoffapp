package com.messoff.pulso.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val status: Int, val code: String = "ERROR") : Exception(message)

/**
 * Cliente de la API de Pulso. Usa el mismo backend que la web: la cookie de sesión
 * se captura al entrar y se envía como `Authorization: Bearer`, que el servidor acepta.
 */
class Api(private val session: Session) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    var onUnauthorized: (() -> Unit)? = null

    suspend fun raw(method: String, path: String, body: JsonElement? = null): String =
        withContext(Dispatchers.IO) {
            val base = session.baseUrl
            if (base.isBlank()) throw ApiException("Configura la dirección del servidor.", 0, "NO_SERVER")
            val builder = Request.Builder()
                .url("$base/api$path")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json")
            session.token?.let { builder.header("Authorization", "Bearer $it") }
            val requestBody = when {
                body != null -> body.toString().toRequestBody(jsonType)
                method == "GET" -> null
                else -> "{}".toRequestBody(jsonType)
            }
            builder.method(method, requestBody)

            val response = try {
                client.newCall(builder.build()).execute()
            } catch (error: IOException) {
                throw ApiException("No se pudo conectar con el servidor. Comprueba tu conexión.", 0, "NETWORK")
            } catch (error: IllegalArgumentException) {
                throw ApiException("La dirección del servidor no es válida.", 0, "BAD_URL")
            }

            response.use { res ->
                // La sesión viaja en la cookie: la guardamos como token.
                res.headers("Set-Cookie").forEach { header ->
                    val pair = header.substringBefore(';')
                    if (pair.startsWith("pulso_session=")) {
                        session.token = pair.substringAfter('=').ifBlank { null }
                    }
                }
                val text = res.body.string()
                if (!res.isSuccessful) {
                    val payload = runCatching { json.decodeFromString(ErrorResponse.serializer(), text) }.getOrNull()
                    val message = payload?.error
                        ?: if (res.code == 401) "Tu sesión ha caducado. Vuelve a entrar." else "No se pudo completar la acción."
                    if (res.code == 401 && !path.startsWith("/auth/login") && !path.startsWith("/auth/register")) {
                        onUnauthorized?.invoke()
                    }
                    throw ApiException(message, res.code, payload?.code ?: "ERROR")
                }
                text.ifBlank { "{}" }
            }
        }

    suspend inline fun <reified T> get(path: String): T = json.decodeFromString(raw("GET", path))

    suspend inline fun <reified T> post(path: String, body: JsonElement? = null): T =
        json.decodeFromString(raw("POST", path, body))

    suspend inline fun <reified T> patch(path: String, body: JsonElement): T =
        json.decodeFromString(raw("PATCH", path, body))

    suspend fun delete(path: String) {
        raw("DELETE", path)
    }

    inline fun <reified T> encode(value: T): JsonElement = json.encodeToJsonElement(value)

    fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((key, value) in pairs) {
            put(
                key,
                when (value) {
                    null -> JsonNull
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is JsonElement -> value
                    else -> JsonPrimitive(value.toString())
                },
            )
        }
    }
}
