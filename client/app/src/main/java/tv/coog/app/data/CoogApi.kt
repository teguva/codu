package tv.coog.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CoogApi(
    private val serverUrl: String,
    private val token: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
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

    suspend fun playbackSession(
        mediaId: String = "",
        jobId: String = "",
        imdbId: String = "",
        kind: String = "",
        title: String = "",
        year: Int = 0,
        season: Int = 0,
        episode: Int = 0,
    ): PlaybackSession {
        val body = json.encodeToString(
            PlaybackSessionRequest.serializer(),
            PlaybackSessionRequest(
                mediaId = mediaId,
                jobId = jobId,
                imdbId = imdbId,
                kind = kind,
                title = title,
                year = year,
                season = season,
                episode = episode,
            ),
        )
        return withContext(Dispatchers.IO) {
            val req = request("/api/v1/playback/sessions")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val session = runCatching { json.decodeFromString<PlaybackSession>(text) }.getOrNull()
                if (session != null && (resp.isSuccessful || session.jobId.isNotBlank())) {
                    return@use session
                }
                throw ApiException(resp.code, text.ifBlank { resp.message })
            }
        }
    }

    suspend fun trailerExists(id: String): Boolean = withContext(Dispatchers.IO) {
        val req = request("/api/v1/media/$id/trailer").head().build()
        client.newCall(req).execute().use { resp ->
            resp.code != 404 && resp.code < 500
        }
    }

    suspend fun catalogHome(): CatalogHomeResponse = get("/api/v1/catalog/home")

    suspend fun catalogShow(imdbId: String): CatalogShowResponse = get("/api/v1/catalog/series/$imdbId")

    suspend fun catalogTitle(imdbId: String, kind: String = "movie"): MediaItem {
        val k = kind.ifBlank { "movie" }
        return get("/api/v1/catalog/title/$imdbId?kind=${enc(k)}")
    }

    suspend fun catalogTmdb(kind: String, tmdbId: Int): MediaItem =
        get("/api/v1/catalog/tmdb/${enc(kind.ifBlank { "movie" })}/$tmdbId")

    suspend fun catalogStreams(imdbId: String, kind: String = "movie", season: Int = 0, episode: Int = 0): List<StreamCandidate> {
        val q = buildString {
            append("/api/v1/catalog/streams?imdb=${enc(imdbId)}")
            append("&kind=${enc(kind.ifBlank { "movie" })}")
            if (season > 0) append("&season=$season")
            if (episode > 0) append("&episode=$episode")
        }
        return get<StreamsResponse>(q).items
    }

    suspend fun catalogSearch(q: String): SearchResponse = get("/api/v1/catalog/search?q=${enc(q)}")

    suspend fun catalogPerson(tmdbId: Int): PersonSummary = get("/api/v1/catalog/person/$tmdbId")

    suspend fun catalogSimilar(imdbId: String, kind: String = "movie"): List<MediaItem> =
        get<CatalogItemsResponse>("/api/v1/catalog/title/$imdbId/similar?kind=${enc(kind.ifBlank { "movie" })}").items

    suspend fun jobs(): List<JobItem> = get<JobsResponse>("/api/v1/jobs").items

    suspend fun enqueueJob(url: String, title: String = ""): JobItem {
        val body = json.encodeToString(
            EnqueueJobRequest.serializer(),
            EnqueueJobRequest(url = url, title = title),
        )
        return post("/api/v1/jobs", body)
    }

    suspend fun enqueueDebrid(
        imdbId: String,
        infoHash: String,
        title: String = "",
        kind: String = "movie",
        season: Int = 0,
        episode: Int = 0,
        year: Int = 0,
    ): JobItem {
        val body = json.encodeToString(
            EnqueueJobRequest.serializer(),
            EnqueueJobRequest(
                type = "debrid",
                imdbId = imdbId,
                infoHash = infoHash,
                title = title,
                kind = kind,
                season = season,
                episode = episode,
                year = year,
            ),
        )
        return post("/api/v1/jobs", body)
    }

    suspend fun job(id: String): JobItem = get("/api/v1/jobs/$id")

    suspend fun pauseJob(id: String): JobItem = post("/api/v1/jobs/$id/pause", "{}")

    suspend fun cancelJob(id: String): JobItem = post("/api/v1/jobs/$id/cancel", "{}")

    suspend fun retryJob(id: String): JobItem = post("/api/v1/jobs/$id/retry", "{}")

    suspend fun reportEvent(
        type: String,
        message: String,
        mediaId: String = "",
        jobId: String = "",
        sessionId: String = "",
        level: String = "error",
    ) {
        val body = json.encodeToString(
            ClientEventRequest.serializer(),
            ClientEventRequest(
                level = level,
                type = type,
                message = message,
                mediaId = mediaId,
                jobId = jobId,
                sessionId = sessionId,
            ),
        )
        withContext(Dispatchers.IO) {
            val req = request("/api/v1/client/events")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(req).execute().close()
        }
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

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

class ApiException(val code: Int, override val message: String) : RuntimeException(message)
