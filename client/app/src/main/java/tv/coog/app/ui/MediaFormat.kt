package tv.coog.app.ui

import androidx.compose.ui.graphics.Color
import tv.coog.app.data.MediaItem

private val showSuffixRe = Regex("""\s*[·•]\s*S\d{1,2}E\d{1,3}.*""", RegexOption.IGNORE_CASE)
private val releaseTagRe = Regex(
    """(?i)\b(?:2160p|1080p|720p|480p|576p|4k|uhd|hdr10\+?|hdr|hlg|dv|dovi|dolby[\s.]?vision|web[\s.-]?dl|webrip|web|bluray|blu[\s.-]?ray|remux|hybrid|proper|repack|internal|amzn|nf|atvp|dsnp|hulu|hmax|ddp?(?:[.\s]\d(?:\.\d)?)?|atmos|truehd|dts(?:-hd)?|aac(?:[\s.-]lc)?|ac3|eac3|flac|opus|h[\s.-]?265|h[\s.-]?264|x265|x264|hevc|avc|av1|10bit|8bit|hdr10plus|multi|subs?|dubbed|extended|unrated|directors[\s.]?cut|eztv|bigdoc|megusta|blacktv)\b""",
)
private val squareBracketsRe = Regex("\\[[^]]*]")
private val curlyBracketsRe = Regex("\\{[^{}]*\\}")
private val groupTailRe = Regex("""\s[-–]\s*[A-Za-z0-9][A-Za-z0-9._-]*$""")
private val extraSpacesRe = Regex("""\s+""")

fun cleanReleaseName(raw: String): String {
    var s = squareBracketsRe.replace(raw, " ")
    s = curlyBracketsRe.replace(s, " ")
    s = s.replace('【', ' ').replace('】', ' ')
    s = s.replace('.', ' ').replace('_', ' ')
    s = releaseTagRe.replace(s, " ")
    s = groupTailRe.replace(s, "")
    s = extraSpacesRe.replace(s, " ").trim(' ', '-', '.', '·')
    return s
}

fun MediaItem.isTrailer(): Boolean {
    val file = path.substringAfterLast('/').lowercase()
    val stem = file.substringBeforeLast('.')
    return stem == "trailer" || stem.startsWith("trailer-") || title.equals("trailer", ignoreCase = true)
}

fun MediaItem.seriesName(): String {
    val raw = showTitle.ifBlank { title }
    return showSuffixRe.replace(raw, "").trim().ifBlank { "Series" }
}

fun MediaItem.headline(): String = when (kind) {
    "episode" -> seriesName()
    else -> cleanReleaseName(title).ifBlank { title }
}

fun MediaItem.episodeHeadline(): String {
    val ep = seasonEpisode()
    val name = episodeName()
    return when {
        ep.isNotBlank() && name.isNotBlank() && !name.equals(ep, ignoreCase = true) -> "$ep  ·  $name"
        ep.isNotBlank() -> ep
        name.isNotBlank() -> name
        else -> headline()
    }
}

fun MediaItem.episodeName(): String {
    var s = cleanReleaseName(title)
    val show = cleanReleaseName(seriesName())
    if (show.isNotBlank() && s.startsWith(show, ignoreCase = true)) {
        s = s.substring(show.length).trim(' ', '-', ':', '·')
    }
    s = s.replace(Regex("""(?i)^S\d{1,2}E\d{1,3}\s*"""), "").trim()
    if (s.isBlank() && episode > 0) return "Episode $episode"
    return s
}

fun MediaItem.supporting(): String {
    if (kind == "episode") {
        return listOfNotNull(
            seasonEpisode().ifBlank { null },
            resolutionLabel(),
        ).joinToString("  ·  ")
    }
    return listOfNotNull(
        year.takeIf { it > 0 }?.toString(),
        resolutionLabel(),
        hdrLabel(),
        formatDuration(durationMs),
    ).joinToString("  ·  ")
}

fun MediaItem.seasonEpisode(): String {
    if (season <= 0 && episode <= 0) return ""
    return "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
}

fun MediaItem.resolutionLabel(): String? = when {
    width >= 3800 -> "4K"
    width >= 2500 -> "1440p"
    width >= 1800 -> "1080p"
    width >= 1200 -> "720p"
    width > 0 -> "${width}p"
    else -> null
}

fun MediaItem.hdrLabel(): String? = when (hdr.lowercase()) {
    "dolbyvision", "dovi" -> "Dolby Vision"
    "hdr10" -> "HDR10"
    "hlg" -> "HLG"
    else -> hdr.ifBlank { null }?.uppercase()
}

fun MediaItem.techLine(): String = listOfNotNull(
    codecVideo.ifBlank { null }?.uppercase(),
    codecAudio.ifBlank { null }?.uppercase(),
    if (width > 0 && height > 0) "${width}×${height}" else resolutionLabel(),
    hdrLabel(),
    formatDuration(durationMs),
).joinToString("  ·  ")

fun formatDuration(ms: Long): String? {
    if (ms < 60_000) return null
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

fun formatClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun MediaItem.posterColors(): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFF1D4E89) to Color(0xFF0B1F33),
        Color(0xFF6B2D5B) to Color(0xFF241018),
        Color(0xFF2F6F4E) to Color(0xFF102018),
        Color(0xFF8A4B12) to Color(0xFF2A1608),
        Color(0xFF3F3D9B) to Color(0xFF12122A),
        Color(0xFF7A2F2F) to Color(0xFF220E0E),
        Color(0xFF1F6F73) to Color(0xFF0C2426),
        Color(0xFF4A5D23) to Color(0xFF161C0C),
    )
    val idx = (id.hashCode() and 0x7FFFFFFF) % palette.size
    return palette[idx]
}

fun MediaItem.monogram(): String {
    val src = headline().trim()
    val parts = src.split(Regex("""[\s._-]+""")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        src.isNotEmpty() -> src.take(2).uppercase()
        else -> "CO"
    }
}

fun MediaItem.libraryBucket(): String {
    val marker = "/Videos/"
    val idx = path.indexOf(marker, ignoreCase = true)
    if (idx >= 0) {
        val rest = path.substring(idx + marker.length)
        return rest.substringBefore('/').ifBlank { "Videos" }
    }
    val parts = path.trimEnd('/').split('/').filter { it.isNotEmpty() }
    return parts.getOrNull(parts.size - 3) ?: parts.getOrNull(parts.size - 2) ?: "Videos"
}

data class ShowRow(
    val name: String,
    val episodes: List<MediaItem>,
) {
    val cover: MediaItem get() = episodes.first()
    val subtitle: String get() {
        val seasons = episodes.map { it.season }.filter { it > 0 }.distinct().size
        val n = episodes.size
        return when {
            seasons > 1 -> "$seasons seasons  ·  $n episodes"
            else -> "$n episodes"
        }
    }
}

data class FolderRow(
    val name: String,
    val items: List<MediaItem>,
) {
    val cover: MediaItem get() = items.first()
    val subtitle: String get() {
        val n = items.size
        return if (n == 1) "1 video" else "$n videos"
    }
}

fun List<MediaItem>.movieItems(): List<MediaItem> =
    filter { it.kind == "movie" }.sortedBy { it.headline().lowercase() }

fun List<MediaItem>.showRows(): List<ShowRow> =
    filter { it.kind == "episode" && !it.isTrailer() }
        .groupBy { it.seriesName() }
        .map { (name, eps) ->
            ShowRow(
                name = name,
                episodes = eps.sortedWith(compareBy({ it.season }, { it.episode }, { it.title })),
            )
        }
        .sortedBy { it.name.lowercase() }

fun List<MediaItem>.folderRows(): List<FolderRow> =
    filter { it.kind != "movie" && it.kind != "episode" && !it.isTrailer() }
        .groupBy { it.libraryBucket() }
        .filterKeys { it.lowercase() !in setOf("movies", "series") }
        .map { (name, files) ->
            FolderRow(
                name = name,
                items = files.sortedBy { it.headline().lowercase() },
            )
        }
        .sortedBy { it.name.lowercase() }

fun MediaItem.kindLabel(): String = when (kind) {
    "movie" -> "Movie"
    "episode" -> "Series"
    else -> "Video"
}

fun MediaItem.heroSubtitle(): String = tagline.trim()

fun MediaItem.heroDescription(): String {
    val plot = plot.trim()
    val tag = heroSubtitle()
    if (plot.isBlank()) return ""
    if (tag.isNotBlank() && plot.equals(tag, ignoreCase = true)) return ""
    return plot
}

fun MediaItem.heroChips(rowLabel: String = kindLabel()): List<String> {
    val chips = mutableListOf<String>()
    if (rowLabel.isNotBlank()) chips.add(rowLabel)
    year.takeIf { it > 0 }?.toString()?.let { chips.add(it) }
    formatDuration(durationMs)?.let { chips.add(it) }
    if (rating > 0) chips.add("★ ${"%.1f".format(rating)}")
    chips.addAll(genres.map { it.trim() }.filter { it.isNotBlank() }.take(3))
    return chips
}

fun List<MediaItem>.librarySummary(): String {
    val movies = movieItems().size
    val shows = showRows().size
    val folders = folderRows()
    val extra = folders.sumOf { it.items.size }
    val parts = buildList {
        if (movies > 0) add(if (movies == 1) "1 movie" else "$movies movies")
        if (shows > 0) add(if (shows == 1) "1 series" else "$shows series")
        if (extra > 0) add("$extra videos")
    }
    return if (parts.isEmpty()) "Library ready" else parts.joinToString("  ·  ") + " ready to play"
}
