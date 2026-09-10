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
    "series" -> cleanReleaseName(title).ifBlank { title }
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

fun friendlyPlayError(raw: String): String {
    val text = raw.trim()
    val lower = text.lowercase()
    if (text.startsWith("Real-Debrid", ignoreCase = true) && text.length > 48) {
        return text
    }
    return when {
        "451" in lower || "infringing" in lower || "blocklist" in lower ->
            "Real-Debrid blocked this torrent: the filename or hash is on their blocklist. Pick another source — Cached / RD+ usually work."
        lower.contains("invalid_token") || lower.contains("bad_token") ||
            (lower.contains("real-debrid") && "401" in lower) ->
            "Real-Debrid rejected the API token. Update it in Settings."
        lower.contains("traffic") && lower.contains("real-debrid") ->
            "Real-Debrid traffic limit reached. Wait for reset or upgrade the account."
        lower.contains("real-debrid") ->
            "Real-Debrid could not start this file. Try another source."
        else -> text
    }
}

fun formatClock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun playbackDurationMs(exoDuration: Long, expectedMs: Long, bufferedMs: Long, streaming: Boolean): Long {
    val exo = if (exoDuration > 0) exoDuration else 0L
    if (expectedMs > 0) {
        return maxOf(expectedMs, exo)
    }
    if (streaming && exo > 0 && bufferedMs > 0 && exo <= bufferedMs + 5_000L) {
        return 0L
    }
    return exo
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
    val header: MediaItem? = null,
) {
    val cover: MediaItem get() = header ?: episodes.firstOrNull() ?: MediaItem(id = "", kind = "series", title = name)

    fun asFeaturedItem(): MediaItem = cover.copy(
        kind = "series",
        title = name.ifBlank { cover.title },
    )
    val subtitle: String get() {
        val seasons = episodes.map { it.season }.filter { it > 0 }.distinct().size
        val n = episodes.size
        val local = episodes.count { it.isLocal() }
        return when {
            local > 0 && local < n -> "$local of $n on disk"
            seasons > 1 -> "$seasons seasons  ·  $n episodes"
            else -> "$n episodes"
        }
    }
}

fun mergeShowEpisodes(catalog: List<MediaItem>, local: List<MediaItem>): List<MediaItem> {
    if (catalog.isEmpty()) return local
    if (local.isEmpty()) return catalog
    val localBySE = LinkedHashMap<Pair<Int, Int>, MediaItem>()
    for (ep in local) {
        localBySE.putIfAbsent(ep.season to ep.episode, ep)
    }
    val seen = HashSet<Pair<Int, Int>>()
    val out = ArrayList<MediaItem>(catalog.size + local.size)
    for (ep in catalog) {
        val key = ep.season to ep.episode
        seen.add(key)
        val loc = localBySE[key]
        out.add(
            if (loc == null) ep
            else ep.copy(
                inLibrary = true,
                libraryId = loc.libraryId.ifBlank { loc.id },
                path = loc.path.ifBlank { ep.path },
            ),
        )
    }
    local.filter { (it.season to it.episode) !in seen }
        .sortedWith(compareBy({ it.season }, { it.episode }, { it.title }))
        .forEach { out.add(it) }
    return out
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

fun List<MediaItem>.movieItems(): List<MediaItem> {
    val showNames = showRows().map { it.name }
    return filter { item ->
        if (item.kind != "movie") return@filter false
        if (item.season > 0 || item.episode > 0 || item.showTitle.isNotBlank()) return@filter false
        if (looksLikeEpisodeFile(item)) return@filter false
        showNames.none { matchesShowTitle(item.headline(), it) }
    }.sortedBy { it.headline().lowercase() }
}

fun overlayCatalog(local: List<MediaItem>, catalog: List<MediaItem>): List<MediaItem> {
    if (local.isEmpty() || catalog.isEmpty()) return local
    return local.map { item ->
        if (item.imdbId.isNotBlank() || item.tmdbId != 0) item
        else {
            val want = normalizeBrowseTitle(item.headline())
            if (want.isBlank()) item
            else {
                val hits = catalog.filter { normalizeBrowseTitle(it.headline()) == want }
                val hit = hits.firstOrNull { item.year == 0 || it.year == 0 || it.year == item.year }
                    ?: hits.singleOrNull()
                if (hit != null) mergeDetails(item, hit) else item
            }
        }
    }
}

fun matchesShowTitle(title: String, showName: String): Boolean {
    val a = normalizeBrowseTitle(title)
    val b = normalizeBrowseTitle(showName)
    return a.isNotBlank() && b.isNotBlank() && (a == b || a.startsWith("$b "))
}

private val yearSuffixRe = Regex("""\s*\(\d{4}\)\s*$""")

fun normalizeBrowseTitle(raw: String): String =
    yearSuffixRe.replace(cleanReleaseName(raw), "")
        .replace('’', '\'')
        .replace('‘', '\'')
        .lowercase()
        .trim()

private val episodePathRe = Regex("""(?i)(?:[/\\]season\s*\d+|s\d{1,2}e\d{1,3})""")

private fun looksLikeEpisodeFile(item: MediaItem): Boolean =
    episodePathRe.containsMatchIn("${item.path} ${item.title}")

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
    "episode", "series" -> "Series"
    else -> "Video"
}

fun MediaItem.hasOfficialMeta(): Boolean = when (matchStatus.lowercase()) {
    "unmatched", "ignored", "suggested" -> false
    "matched" -> true
    else -> path.isBlank() || imdbId.isNotBlank()
}

fun MediaItem.heroGenres(): List<String> =
    if (!hasOfficialMeta()) emptyList()
    else genres.map { it.trim() }.filter { it.isNotBlank() }.take(3)

fun MediaItem.heroMetaLine(): String {
    if (!hasOfficialMeta() && year <= 0 && durationMs <= 0 && runtimeMinutes <= 0) return ""
    val runtime = when {
        runtimeMinutes > 0 -> formatDuration(runtimeMinutes * 60_000L)
        durationMs > 0 -> formatDuration(durationMs)
        else -> null
    }
    return listOfNotNull(
        country.takeIf { hasOfficialMeta() && it.isNotBlank() },
        year.takeIf { it > 0 }?.toString(),
        certification.takeIf { hasOfficialMeta() && it.isNotBlank() },
        runtime,
    ).joinToString("  ·  ")
}

fun MediaItem.heroSubtitle(): String {
    if (!hasOfficialMeta()) return ""
    return tagline.trim()
}

fun MediaItem.heroDescription(): String {
    if (!hasOfficialMeta()) return ""
    val plot = plot.trim()
    val tag = heroSubtitle()
    if (plot.isBlank()) return ""
    if (tag.isNotBlank() && plot.equals(tag, ignoreCase = true)) return ""
    return plot
}

fun MediaItem.heroChips(rowLabel: String = kindLabel(), jobs: List<tv.coog.app.data.JobItem> = emptyList()): List<String> {
    val chips = mutableListOf<String>()
    posterBadgeLabel(jobs)?.let { chips.add(it) }
    if (rowLabel.isNotBlank()) chips.add(rowLabel)
    year.takeIf { it > 0 }?.toString()?.let { chips.add(it) }
    formatDuration(durationMs)?.let { chips.add(it) }
    if (hasOfficialMeta() && rating > 0) chips.add("★ ${"%.1f".format(rating)}")
    if (hasOfficialMeta()) chips.addAll(genres.map { it.trim() }.filter { it.isNotBlank() }.take(3))
    return chips
}

fun MediaItem.posterBadgeLabel(jobs: List<tv.coog.app.data.JobItem> = emptyList()): String? {
    val imdb = imdbId
    val job = if (imdb.isNotBlank()) {
        jobs.firstOrNull { it.imdbId.equals(imdb, ignoreCase = true) && it.status != "finished" && it.status != "cancelled" && it.status != "error" }
    } else {
        null
    }
    return when {
        job?.status == "queued" -> "Fetching"
        job != null -> {
            val pct = (job.progress * 100).toInt()
            when {
                pct > 0 -> "↓ $pct%"
                job.ready -> "Ready"
                else -> "Downloading"
            }
        }
        path.isNotBlank() || inLibrary -> "Local"
        releasePhase == "coming_soon" -> "Coming soon"
        releasePhase == "theatrical" -> "In theatres"
        else -> null
    }
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
