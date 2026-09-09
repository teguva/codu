package tv.coog.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CoogApi(
    private val serverUrl: String,
    private val token: String,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun request(path: String): Request.Builder {
        val builder = Request.Builder().url(serverUrl.trimEnd('/') + path)
        if (token.isNotBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder
    }

    suspend fun health(): HealthResponse = get("/health")

    suspend fun library(): List<MediaItem> = get<LibraryResponse>("/api/v1/library").items

    suspend fun item(id: String): MediaItem = get("/api/v1/library/$id")

    suspend fun playbackSession(mediaId: String): PlaybackSession {
        val body = json.encodeToString(
            PlaybackSessionRequest.serializer(),
            PlaybackSessionRequest(mediaId = mediaId),
        )
        return post("/api/v1/playback/sessions", body)
    }

    private suspend inline fun <reified T> get(path: String): T {
        val req = request(path).get().build()
        return execute(req)
    }

    private suspend inline fun <reified T> post(path: String, body: String): T {
        val req = request(path)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return execute(req)
    }

    private suspend inline fun <reified T> execute(req: Request): T = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, text.ifBlank { resp.message })
            }
            json.decodeFromString(text)
        }
    }
}

class ApiException(val code: Int, override val message: String) : RuntimeException(message)
