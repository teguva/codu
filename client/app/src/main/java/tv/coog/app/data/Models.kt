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
    val tagline: String = "",
    val plot: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double = 0.0,
    @SerialName("posterUrl") val posterUrl: String = "",
    @SerialName("backdropUrl") val backdropUrl: String = "",
)

@Serializable
data class PlaybackSessionRequest(
    val mediaId: String,
    val clientCapabilities: ClientCapabilities? = ClientCapabilities(),
)

@Serializable
data class ClientCapabilities(
    val videoCodecs: List<String> = listOf("h264", "hevc", "vp9", "av1"),
    val audioCodecs: List<String> = listOf("aac", "ac3", "eac3", "opus", "mp3"),
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
)
