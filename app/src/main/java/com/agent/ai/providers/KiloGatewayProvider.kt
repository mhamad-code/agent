package com.agent.ai.providers

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The Kilo AI Gateway: OpenAI compatible, streaming over server-sent events.
 *
 * Verified against https://kilo.ai/docs/gateway (November 2026):
 *  - base URL `https://ai-gateway.kilo.ai/api/v1`
 *  - `POST /chat/completions` with `"stream": true`
 *  - `GET /models` needs no authentication
 *  - anonymous access works for free models only (200 requests per hour per IP), the
 *    default is the Kilo provided auto free model (`kilo-auto/free`).
 *
 * No API key is used or stored, so the app can never spend someone's credits.
 */
class KiloGatewayProvider(
    private val client: OkHttpClient = defaultClient(),
    private val clientVersion: String = "agent-android",
) : Provider {

    override val id: String = "kilo"
    override val displayName: String = "Kilo Gateway"

    @Volatile
    private var activeCall: Call? = null

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models")
            .header("Accept", "application/json")
            .header("X-KiloCode-Version", clientVersion)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ProviderException(errorForStatus(response.code, body))
                }
                parseModels(body)
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: IOException) {
            throw ProviderException(mapIOException(e))
        }
    }

    override fun chat(request: ChatRequest): Flow<ProviderEvent> = channelFlow {
        val httpRequest = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .header("X-KiloCode-Version", clientVersion)
            .post(request.toJson().toString().toRequestBody(JsonMedia))
            .build()

        val call = client.newCall(httpRequest)
        activeCall = call
        val producer = this

        val reader = launch(Dispatchers.IO) {
            var sawFailure = false
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                        sawFailure = true
                        producer.send(ProviderEvent.Failed(errorForStatus(response.code, body, response)))
                        return@use
                    }

                    val source = response.body?.source()
                    if (source == null) {
                        sawFailure = true
                        producer.send(ProviderEvent.Failed(ProviderError(ProviderErrorKind.Unknown)))
                        return@use
                    }

                    var done = false
                    var truncated = false
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val parsed = SseStreamParser.parse(line)
                        if (parsed.truncated) truncated = true
                        for (event in parsed.events) {
                            if (event is ProviderEvent.Failed) sawFailure = true
                            producer.send(event)
                        }
                        if (parsed.done) {
                            done = true
                            break
                        }
                    }

                    if (!sawFailure) producer.send(ProviderEvent.Finished(null, truncated))
                    if (!done) call.cancel()
                }
            } catch (e: IOException) {
                // A cancelled call throws here as well; that is not an error to show.
                if (!call.isCanceled() && !sawFailure) {
                    producer.send(ProviderEvent.Failed(mapIOException(e)))
                }
            } finally {
                activeCall = null
                producer.close()
            }
        }

        awaitClose {
            reader.cancel()
            call.cancel()
            activeCall = null
        }
    }

    override fun cancel() {
        activeCall?.cancel()
    }

    private fun mapIOException(e: IOException): ProviderError = when (e) {
        is SocketTimeoutException -> ProviderError(ProviderErrorKind.Timeout, e.message.orEmpty())
        is UnknownHostException, is ConnectException ->
            ProviderError(ProviderErrorKind.NoInternet, e.message.orEmpty())
        else -> ProviderError(ProviderErrorKind.NoInternet, e.message.orEmpty())
    }

    private fun errorForStatus(
        code: Int,
        body: String,
        response: okhttp3.Response? = null,
    ): ProviderError {
        val detail = extractErrorMessage(body)
        return when {
            code == 429 -> ProviderError(
                kind = ProviderErrorKind.RateLimited,
                detail = detail,
                statusCode = code,
                retryAfterSeconds = response?.header("Retry-After")?.trim()?.toIntOrNull(),
            )

            code == 404 || (code == 400 && detail.contains("model", ignoreCase = true)) ->
                ProviderError(ProviderErrorKind.ModelGone, detail, code)

            code in 500..599 -> ProviderError(ProviderErrorKind.ServerError, detail, code)

            code == 401 || code == 403 -> ProviderError(
                ProviderErrorKind.ModelGone,
                detail.ifBlank { "This model needs a Kilo API key." },
                code,
            )

            else -> ProviderError(ProviderErrorKind.Unknown, detail, code)
        }
    }

    private fun extractErrorMessage(body: String): String {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return ""
        val error = root["error"] as? JsonObject
        return error?.string("message")
            ?: root.string("message")
            ?: error?.string("type")
            ?: ""
    }

    /**
     * One short non-streaming answer for titles and compaction. It uses its own call, so it
     * can never cancel the reply that is streaming on the chat screen.
     */
    override suspend fun complete(request: ChatRequest): String = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-KiloCode-Version", clientVersion)
            .post(request.toJson(stream = false).toString().toRequestBody(JsonMedia))
            .build()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ProviderException(errorForStatus(response.code, body))
                val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                    ?: return@use ""
                val message = root.firstChoice()?.get("message") as? JsonObject
                message?.string("content").orEmpty()
            }
        } catch (e: ProviderException) {
            throw e
        } catch (e: IOException) {
            throw ProviderException(mapIOException(e))
        }
    }

    private fun ChatRequest.toJson(stream: Boolean = true): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", stream)
        maxTokens?.let { put("max_tokens", it) }
        temperature?.let { put("temperature", it) }
        // The standard OpenAI compatible field, sent only for models that reason.
        if (reasoning) put("reasoning_effort", "medium")
        putJsonArray("messages") {
            messages.forEach { message ->
                add(
                    buildJsonObject {
                        put("role", message.role.wire)
                        if (message.images.isEmpty()) {
                            put("content", message.content)
                        } else {
                            put("content", imageContent(message))
                        }
                    }
                )
            }
        }
    }

    /** A vision request carries the text plus each image as a `data:` URL. */
    private fun imageContent(message: WireMessage): JsonArray = buildJsonArray {
        if (message.content.isNotBlank()) {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", message.content)
                }
            )
        }
        message.images.forEach { url ->
            add(
                buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", url) }
                }
            )
        }
    }

    companion object {
        const val BASE_URL = "https://ai-gateway.kilo.ai/api/v1"

        /** The Kilo provided free tier, which rotates through available free models. */
        const val AUTO_FREE_MODEL = "kilo-auto/free"

        private val JsonMedia = "application/json; charset=utf-8".toMediaType()

        /**
         * Vision, reasoning and tool support, as the gateway reports them. A field that is
         * absent means the model does not support it; nothing is assumed.
         */
        fun capabilitiesOf(obj: JsonObject): ModelCapabilities {
            val modalities = (obj["architecture"] as? JsonObject)?.get("input_modalities") as? JsonArray
            val vision = modalities?.any { element ->
                (element as? kotlinx.serialization.json.JsonPrimitive)?.content == "image"
            } ?: false
            val params = obj["supported_parameters"] as? JsonArray
            val names = params?.mapNotNull { element ->
                (element as? kotlinx.serialization.json.JsonPrimitive)?.content
            }.orEmpty()
            return ModelCapabilities(
                vision = vision,
                reasoning = names.any { it.equals("reasoning", ignoreCase = true) },
                toolCalling = names.any { it.equals("tools", ignoreCase = true) },
            )
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // A reply can stall between tokens, so the read timeout is generous.
            .readTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Parses `GET /models`; only models usable without a key are kept. */
        fun parseModels(body: String): List<ModelInfo> {
            val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return emptyList()
            val data = root["data"] as? JsonArray ?: return emptyList()
            val models = data.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.string("id") ?: return@mapNotNull null
                val pricing = obj["pricing"] as? JsonObject
                val promptPrice = (pricing?.get("prompt") as? kotlinx.serialization.json.JsonPrimitive)?.content
                val completionPrice =
                    (pricing?.get("completion") as? kotlinx.serialization.json.JsonPrimitive)?.content
                val explicitlyFree = id.endsWith(":free") || id.startsWith("kilo-auto/")
                val zeroPriced = promptPrice == "0" && completionPrice == "0"
                ModelInfo(
                    id = id,
                    name = obj.string("name") ?: id,
                    free = explicitlyFree || zeroPriced,
                    contextLength = (obj["context_length"] as? kotlinx.serialization.json.JsonPrimitive)
                        ?.content?.toIntOrNull()
                        ?: ((obj["top_provider"] as? JsonObject)?.get("context_length") as? kotlinx.serialization.json.JsonPrimitive)
                            ?.content?.toIntOrNull()
                        ?: 0,
                    description = obj.string("description").orEmpty(),
                    capabilities = capabilitiesOf(obj),
                )
            }
            return models
                .filter { it.free }
                .sortedWith(compareBy({ it.id != AUTO_FREE_MODEL }, { it.id }))
        }
    }
}
