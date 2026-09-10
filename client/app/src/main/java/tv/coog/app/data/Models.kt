package tv.coog.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String = "",
    val version: String = "",
    val ffmpeg: String = "",
)

@Serializable
data class LibraryResponse(
    val items: List<MediaItem> = emptyList(),
)

@Serializable
data class MediaItem(
    val id: String,
    val kind: String = "",
    val title: String = "",
    val year: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    @SerialName("showTitle") val showTitle: String = "",
    val path: String = "",
    @SerialName("codecVideo") val codecVideo: String = "",
    @SerialName("codecAudio") val codecAudio: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val hdr: String = "",
    @SerialName("durationMs") val durationMs: Long = 0,
    @SerialName("contentType") val contentType: String = "",
    @SerialName("imdbId") val imdbId: String = "",
    @SerialName("matchStatus") val matchStatus: String = "",
    val tagline: String = "",
    val plot: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double = 0.0,
    @SerialName("posterUrl") val posterUrl: String = "",
    @SerialName("backdropUrl") val backdropUrl: String = "",
    @SerialName("logoUrl") val logoUrl: String = "",
    @SerialName("inLibrary") val inLibrary: Boolean = false,
    @SerialName("mediaId") val libraryId: String = "",
    @SerialName("releasePhase") val releasePhase: String = "",
    @SerialName("tmdbId") val tmdbId: Int = 0,
    val cast: List<CastMember> = emptyList(),
    val director: CastMember = CastMember(),
    @SerialName("runtimeMinutes") val runtimeMinutes: Int = 0,
    val certification: String = "",
    val country: String = "",
) {
    fun isLocal(): Boolean = path.isNotBlank() || inLibrary || libraryId.isNotBlank()

    fun playableId(): String = when {
        path.isNotBlank() -> id
        libraryId.isNotBlank() -> libraryId
        else -> id
    }

    fun playBlocked(): Boolean = kind != "series" && kind != "episode" &&
        releasePhase == "coming_soon" && !isLocal()
}

@Serializable
data class PlaybackSessionRequest(
    val mediaId: String = "",
    val jobId: String = "",
    val imdbId: String = "",
    val kind: String = "",
    val title: String = "",
    val year: Int = 0,
    val season: Int = 0,
    val episode: Int = 0,
    val clientCapabilities: ClientCapabilities? = ClientCapabilities(),
)

@Serializable
data class ClientCapabilities(
    val videoCodecs: List<String> = listOf("h264", "hevc", "vp9", "av1"),
    val audioCodecs: List<String> = listOf("aac", "ac3", "eac3", "opus", "mp3", "truehd", "dts", "dtshd"),
    val containers: List<String> = listOf("mp4", "mkv", "webm", "ts"),
    val hdr: List<String> = listOf("hdr10", "hlg", "dolbyvision"),
)

@Serializable
data class PlaybackSession(
    val id: String = "",
    val method: String = "",
    val reason: String = "",
    val url: String = "",
    val mediaId: String = "",
    val expectedDurationMs: Long = 0,
    val bufferedMs: Long = 0,
    val error: String = "",
    val jobId: String = "",
)

@Serializable
data class CatalogItemsResponse(
    val items: List<MediaItem> = emptyList(),
)

@Serializable
data class CatalogHomeResponse(
    val trendingMovies: List<MediaItem> = emptyList(),
    val trendingSeries: List<MediaItem> = emptyList(),
)

@Serializable
data class CatalogShowResponse(
    val item: MediaItem = MediaItem(id = ""),
    val episodes: List<MediaItem> = emptyList(),
)

@Serializable
data class CastMember(
    @SerialName("tmdbId") val tmdbId: Int = 0,
    val name: String = "",
    val character: String = "",
    @SerialName("profileUrl") val profileUrl: String = "",
)

@Serializable
data class StreamCandidate(
    @SerialName("infoHash") val infoHash: String = "",
    val title: String = "",
    val name: String = "",
    val quality: String = "",
    val cached: Boolean = false,
    val seeders: Int = 0,
    val size: Long = 0,
    @SerialName("sizeLabel") val sizeLabel: String = "",
    val source: String = "",
    val provider: String = "",
)

@Serializable
data class StreamsResponse(
    val items: List<StreamCandidate> = emptyList(),
)

@Serializable
data class PersonSummary(
    @SerialName("tmdbId") val tmdbId: Int = 0,
    val name: String = "",
    @SerialName("profileUrl") val profileUrl: String = "",
    @SerialName("knownForDepartment") val knownForDepartment: String = "",
    val biography: String = "",
    val birthday: String = "",
    @SerialName("placeOfBirth") val placeOfBirth: String = "",
    val credits: List<MediaItem> = emptyList(),
)

@Serializable
data class SearchResponse(
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val people: List<PersonSummary> = emptyList(),
    val error: String = "",
)

@Serializable
data class EnqueueJobRequest(
    val url: String = "",
    val title: String = "",
    val type: String = "ytdlp",
    val imdbId: String = "",
    val infoHash: String = "",
    val kind: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val year: Int = 0,
)

@Serializable
data class JobsResponse(
    val items: List<JobItem> = emptyList(),
)

@Serializable
data class JobItem(
    val id: String,
    val type: String = "",
    val url: String = "",
    val title: String = "",
    val status: String = "",
    val progress: Double = 0.0,
    val ready: Boolean = false,
    @SerialName("expectedDurationMs") val expectedDurationMs: Long = 0,
    @SerialName("bufferedMs") val bufferedMs: Long = 0,
    val error: String = "",
    @SerialName("mediaId") val mediaId: String = "",
    @SerialName("imdbId") val imdbId: String = "",
    @SerialName("infoHash") val infoHash: String = "",
    @SerialName("logTail") val logTail: String = "",
) {
    fun isActive(): Boolean = status == "queued" || status == "downloading" || status == "ready" || status == "paused"

    fun isFinished(): Boolean = status == "finished"

    fun canPause(): Boolean = status == "queued" || status == "downloading" || status == "ready"

    fun canResume(): Boolean = status == "paused" || status == "error" || status == "cancelled"

    fun canCancel(): Boolean = status != "finished" && status != "cancelled"

    fun headline(): String = title.ifBlank { "Downloading…" }

    fun subtitle(): String {
        val pct = (progress * 100).toInt().coerceIn(0, 99)
        val readyLabel = when {
            expectedDurationMs > 0 && bufferedMs > 0 ->
                "${jobClock(bufferedMs)} / ${jobClock(expectedDurationMs)}"
            pct > 0 -> "$pct%"
            else -> null
        }
        val left = remainingLabel()
        return when {
            status == "error" -> error.ifBlank { "error" }
            status == "finished" -> "Finished"
            status == "queued" -> "Queued"
            status == "paused" -> listOfNotNull("Paused", readyLabel, left).joinToString("  ·  ")
            status == "cancelled" -> "Cancelled"
            ready && status != "finished" -> listOfNotNull("Ready to play", readyLabel, left).joinToString("  ·  ")
            else -> listOfNotNull("Downloading", readyLabel ?: pct.takeIf { it > 0 }?.let { "$it%" }, left).joinToString("  ·  ")
        }
    }

    fun remainingLabel(): String? {
        if (expectedDurationMs <= 0 || bufferedMs <= 0 || bufferedMs >= expectedDurationMs) return null
        val left = expectedDurationMs - bufferedMs
        val minutes = (left / 60_000).toInt().coerceAtLeast(1)
        return if (minutes >= 60) {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h}h left" else "${h}h ${m}m left"
        } else {
            "$minutes min left"
        }
    }
}

private fun jobClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Serializable
data class ClientEventRequest(
    val level: String = "error",
    val type: String,
    val message: String,
    val mediaId: String = "",
    val jobId: String = "",
    val sessionId: String = "",
)
