package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.AnimatedArtStyle
import com.example.model.AppThemeMode
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.Playlist
import com.example.model.ThemeTimerSettings
import com.example.model.Track
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.regex.Pattern

class MusicRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("asd_music_prefs", Context.MODE_PRIVATE)
    private val playCountsMap = mutableMapOf<String, Int>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val metadataFile = File(context.filesDir, "downloaded_tracks_metadata.json")
    private val publicMusicDir by lazy {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "ASDMusic")
            if (!dir.exists()) dir.mkdirs()
            dir
        } catch (_: Exception) {
            downloadsDir
        }
    }

    // Start clean at zero: only device tracks and user added downloads
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<Playlist>>(
        listOf(
            Playlist(
                id = "p_device",
                title = "Device Songs",
                description = "Songs stored on your phone",
                coverEmoji = "📱",
                gradientStart = 0xFF3D2F28,
                gradientEnd = 0xFF241C18,
                tracks = emptyList(),
                createdBy = "Device",
                isCurated = false,
                shareCode = "DEV-001"
            )
        )
    )
    val userPlaylists: StateFlow<List<Playlist>> = _userPlaylists.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            name = "Jafar",
            username = "@jafar4686",
            bio = "Personal Music Vault",
            avatarEmoji = "👤"
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _themeTimerSettings = MutableStateFlow(loadThemeTimerSettings())
    val themeTimerSettings: StateFlow<ThemeTimerSettings> = _themeTimerSettings.asStateFlow()

    init {
        loadPlayCounts()
        // Automatically scan existing downloaded tracks in internal storage
        loadOfflineCache()
        // Attempt to scan device MediaStore songs if permissions are granted
        val scanned = scanDeviceAudio()
        if (scanned == 0) {
            ensureStarterTracksIfEmpty()
        }
    }

    private fun loadThemeTimerSettings(): ThemeTimerSettings {
        return ThemeTimerSettings(
            isAutoTimerEnabled = prefs.getBoolean("auto_theme_timer_enabled", false),
            morningStartHour = prefs.getInt("morning_start_hour", 6),
            morningStartMinute = prefs.getInt("morning_start_minute", 0),
            dayStartHour = prefs.getInt("day_start_hour", 9),
            dayStartMinute = prefs.getInt("day_start_minute", 0),
            nightStartHour = prefs.getInt("night_start_hour", 19),
            nightStartMinute = prefs.getInt("night_start_minute", 0)
        )
    }

    fun updateThemeTimerSettings(settings: ThemeTimerSettings) {
        _themeTimerSettings.value = settings
        prefs.edit()
            .putBoolean("auto_theme_timer_enabled", settings.isAutoTimerEnabled)
            .putInt("morning_start_hour", settings.morningStartHour)
            .putInt("morning_start_minute", settings.morningStartMinute)
            .putInt("day_start_hour", settings.dayStartHour)
            .putInt("day_start_minute", settings.dayStartMinute)
            .putInt("night_start_hour", settings.nightStartHour)
            .putInt("night_start_minute", settings.nightStartMinute)
            .apply()
    }

    fun saveManualThemeMode(mode: AppThemeMode) {
        prefs.edit()
            .putString("manual_theme_mode", mode.name)
            .putBoolean("manual_dark_mode", mode == AppThemeMode.DARK)
            .apply()
    }

    fun loadManualThemeMode(): AppThemeMode {
        val savedName = prefs.getString("manual_theme_mode", null)
        if (savedName != null) {
            return AppThemeMode.entries.firstOrNull { it.name == savedName } ?: AppThemeMode.DAY
        }
        return if (prefs.getBoolean("manual_dark_mode", false)) AppThemeMode.DARK else AppThemeMode.DAY
    }

    fun saveManualDarkMode(isDark: Boolean) {
        saveManualThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.DAY)
    }

    fun loadManualDarkMode(): Boolean = loadManualThemeMode() == AppThemeMode.DARK

    private fun loadPlayCounts() {
        try {
            val raw = prefs.getString("track_play_counts_json", null)
            if (!raw.isNullOrBlank()) {
                val json = JSONObject(raw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    playCountsMap[k] = json.optInt(k, 0).coerceAtLeast(0)
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "loadPlayCounts: ${e.message}")
        }
    }

    private fun savePlayCounts() {
        try {
            val json = JSONObject()
            synchronized(playCountsMap) {
                playCountsMap.forEach { (k, v) -> json.put(k, v) }
            }
            prefs.edit().putString("track_play_counts_json", json.toString()).apply()
        } catch (e: Exception) {
            Log.w("MusicRepository", "savePlayCounts: ${e.message}")
        }
    }

    private fun getSavedPlayCount(trackId: String, title: String): Int {
        synchronized(playCountsMap) {
            val byId = playCountsMap[trackId]
            if (byId != null) return byId
            val titleKey = "title:${title.lowercase().trim()}"
            return playCountsMap[titleKey] ?: 0
        }
    }

    fun incrementPlayCount(trackId: String): Int {
        val track = _tracks.value.firstOrNull { it.id == trackId }
        val newCount = synchronized(playCountsMap) {
            val current = if (track != null) {
                maxOf(track.playCount, getSavedPlayCount(trackId, track.title))
            } else {
                playCountsMap[trackId] ?: 0
            }
            val updated = current + 1
            playCountsMap[trackId] = updated
            if (track != null) {
                playCountsMap["title:${track.title.lowercase().trim()}"] = updated
            }
            updated
        }
        savePlayCounts()

        _tracks.update { list ->
            list.map { if (it.id == trackId) it.copy(playCount = newCount) else it }
        }
        _userPlaylists.update { playlists ->
            playlists.map { pl ->
                pl.copy(tracks = pl.tracks.map { if (it.id == trackId) it.copy(playCount = newCount) else it })
            }
        }
        return newCount
    }

    private fun ensureStarterTracksIfEmpty() {
        if (_tracks.value.isEmpty()) {
            val starters = listOf(
                Track(
                    id = "asd_starter_1",
                    title = "Asd Acoustic Melody",
                    artist = "Asd Studio",
                    album = "Asd Collection Vol. 1",
                    durationSeconds = 186,
                    genre = "Acoustic",
                    isDownloaded = true,
                    localFilePath = null,
                    animatedStyle = AnimatedArtStyle.VINYL,
                    playCount = getSavedPlayCount("asd_starter_1", "Asd Acoustic Melody")
                ),
                Track(
                    id = "asd_starter_2",
                    title = "Vinyl Groove Anthem",
                    artist = "Asd Rhythm",
                    album = "Classic Disc Sessions",
                    durationSeconds = 214,
                    genre = "Vinyl Groove",
                    isDownloaded = true,
                    localFilePath = null,
                    animatedStyle = AnimatedArtStyle.VINYL,
                    playCount = getSavedPlayCount("asd_starter_2", "Vinyl Groove Anthem")
                ),
                Track(
                    id = "asd_starter_3",
                    title = "Sunset Rhythm",
                    artist = "Asd Acoustic",
                    album = "Chill Lounge",
                    durationSeconds = 195,
                    genre = "Acoustic Chill",
                    isDownloaded = true,
                    localFilePath = null,
                    animatedStyle = AnimatedArtStyle.VINYL,
                    playCount = getSavedPlayCount("asd_starter_3", "Sunset Rhythm")
                )
            )
            _tracks.value = starters
        }
    }

    private fun loadOfflineCache() {
        val cachedTracks = mutableListOf<Track>()

        // 1. Load from persistent metadata JSON (retaining exact title, custom title, artist, and local cover picture)
        if (metadataFile.exists()) {
            try {
                val jsonStr = metadataFile.readText()
                if (jsonStr.isNotBlank()) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val filePath = obj.optString("localFilePath")
                        val file = if (filePath.isNotBlank()) File(filePath) else null
                        if (file != null && file.exists() && file.length() > 512) {
                            val trackId = obj.getString("id")
                            val trackTitle = obj.getString("title")
                            val persistedCount = maxOf(
                                obj.optInt("playCount", 0),
                                getSavedPlayCount(trackId, trackTitle)
                            )
                            val track = Track(
                                id = trackId,
                                title = trackTitle,
                                artist = obj.getString("artist"),
                                album = obj.optString("album", "ASD Downloads"),
                                durationSeconds = obj.optInt("durationSeconds", 180),
                                genre = obj.optString("genre", "TikTok Trend"),
                                coverUrl = obj.optString("coverUrl").ifBlank { null },
                                isVideo = obj.optBoolean("isVideo", false),
                                isDownloaded = true,
                                sourceUrl = obj.optString("sourceUrl").ifBlank { null },
                                localFilePath = filePath,
                                animatedStyle = AnimatedArtStyle.VINYL,
                                playCount = persistedCount
                            )
                            cachedTracks.add(track)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Error reading metadata: ${e.message}")
            }
        }

        // 2. Scan for any audio files in downloadsDir or public device storage not yet indexed
        val existingPaths = cachedTracks.mapNotNull { it.localFilePath }.toSet()
        val allSearchFiles = (downloadsDir.listFiles() ?: emptyArray()) + (publicMusicDir.listFiles() ?: emptyArray())
        allSearchFiles.forEach { file ->
            if (file.isFile && !existingPaths.contains(file.absolutePath) &&
                (file.name.endsWith(".mp3") || file.name.endsWith(".mp4") || file.name.endsWith(".m4a"))
            ) {
                val cleanTitle = file.nameWithoutExtension
                    .replace(Regex("^(tiktok|youtube|web|tt|yt)_\\d+_"), "")
                    .replace("_audio", "")
                    .replace("_", " ")
                    .trim()
                    .ifBlank { "Downloaded Song" }

                val coverFile = File(downloadsDir, "${file.nameWithoutExtension.substringBefore("_")}_cover.jpg")
                val coverPath = if (coverFile.exists()) coverFile.absolutePath else null
                val cacheId = "cache_${file.name.hashCode()}"

                val track = Track(
                    id = cacheId,
                    title = cleanTitle,
                    artist = "Saved Song",
                    album = "Device Music",
                    durationSeconds = 180,
                    genre = "Device Audio",
                    coverUrl = coverPath,
                    isDownloaded = true,
                    isVideo = file.name.endsWith(".mp4"),
                    localFilePath = file.absolutePath,
                    animatedStyle = AnimatedArtStyle.VINYL,
                    playCount = getSavedPlayCount(cacheId, cleanTitle)
                )
                cachedTracks.add(track)
            }
        }

        if (cachedTracks.isNotEmpty()) {
            _tracks.update { cachedTracks + it.filter { t -> cachedTracks.none { c -> c.id == t.id } } }
            val dlItems = cachedTracks.map { t ->
                DownloadItem(
                    trackId = t.id,
                    trackTitle = t.title,
                    artist = t.artist,
                    isVideo = t.isVideo,
                    progress = 1.0f,
                    status = DownloadStatus.COMPLETED,
                    filePath = t.localFilePath
                )
            }
            _downloads.update { dlItems }
            persistDownloadedTracks()
        }
    }

    fun scanDeviceAudio(): Int {
        var foundCount = 0
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                val deviceTracks = mutableListOf<Track>()

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Song"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Device Music"
                    val albumId = it.getLong(albumIdCol)
                    val durationMs = it.getLong(durationCol)
                    val dataPath = it.getString(dataCol)

                    val uri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val devId = "device_$id"
                    deviceTracks.add(
                        Track(
                            id = devId,
                            title = title,
                            artist = if (artist == "<unknown>") "Local Artist" else artist,
                            album = album,
                            durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(30),
                            genre = "Device Audio",
                            coverUrl = albumArtUri,
                            isDownloaded = true,
                            localFilePath = dataPath ?: uri.toString(),
                            animatedStyle = AnimatedArtStyle.VINYL,
                            playCount = getSavedPlayCount(devId, title)
                        )
                    )
                    foundCount++
                }

                if (deviceTracks.isNotEmpty()) {
                    _tracks.update { existing ->
                        val existingIds = existing.map { t -> t.id }.toSet()
                        val existingPaths = existing.mapNotNull { t -> t.localFilePath }.toSet()
                        val existingTitles = existing.map { t -> t.title.lowercase().trim() }.toSet()
                        existing + deviceTracks.filter { t ->
                            !existingIds.contains(t.id) &&
                                !existingPaths.contains(t.localFilePath) &&
                                !existingTitles.contains(t.title.lowercase().trim())
                        }
                    }
                    _userPlaylists.update { playlists ->
                        playlists.map { p ->
                            if (p.id == "p_device") p.copy(tracks = deviceTracks) else p
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "scanDeviceAudio: ${e.message}")
        }
        return foundCount
    }

    /**
     * Downloads song/video from a link (TikTok, YouTube, direct MP3/MP4, etc.)
     * and saves it directly to local device storage.
     */
    /**
     * Downloads song/video from a real TikTok link or direct media stream
     * and saves it directly to local device storage.
     */
    private data class ResolvedMedia(
        val streamUrl: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val coverUrl: String? = null,
        val durationSeconds: Int = 180,
        val preferredExt: String? = null,
        val mimeType: String? = null
    )

    /**
     * Automatically detects link source (YouTube, TikTok, Google, or external Web URL),
     * extracts genuine audio/video stream + cover + metadata, downloads it to device storage,
     * and publishes it via MediaStore so all device music players can play it.
     */
    fun addAndDownloadFromLink(
        url: String,
        customTitle: String? = null,
        customArtist: String? = null,
        isVideo: Boolean = false,
        onComplete: (Track) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val cleanUrl = LinkDetector.normalizeUrl(url)
        val trackId = "track_${System.currentTimeMillis()}"

        scope.launch {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // Step 1: Unwrap Google redirects and resolve short links
            var finalUrl = LinkDetector.unwrapGoogleUrl(cleanUrl)
            if (finalUrl.contains("vm.tiktok.com") || finalUrl.contains("vt.tiktok.com") ||
                finalUrl.contains("youtu.be") || finalUrl.contains("bit.ly") ||
                finalUrl.contains("g.co") || finalUrl.contains("goo.gl") ||
                finalUrl.contains("share.google")
            ) {
                try {
                    val headReq = okhttp3.Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()
                    okHttpClient.newCall(headReq).execute().use { resp ->
                        val resolved = resp.request.url.toString()
                        finalUrl = LinkDetector.unwrapGoogleUrl(resolved)
                    }
                } catch (e: Exception) {
                    Log.w("MusicRepository", "Redirect resolution: ${e.message}")
                }
            }

            var resolved = ResolvedMedia()
            var detectedSource = LinkDetector.detectSource(finalUrl)

            // Step 2: Run dedicated extractor pipeline per detected source
            when (detectedSource) {
                LinkSource.YOUTUBE -> {
                    val ytId = LinkDetector.extractYouTubeVideoId(finalUrl)
                    if (ytId != null) {
                        resolved = resolveYouTubeStream(okHttpClient, ytId, isVideo)
                    }
                }
                LinkSource.TIKTOK -> {
                    resolved = resolveTikTokStream(okHttpClient, finalUrl, isVideo)
                }
                LinkSource.GOOGLE, LinkSource.WEB -> {
                    val webResult = resolveGoogleOrWebStream(okHttpClient, finalUrl, isVideo)
                    resolved = webResult.first
                    if (webResult.second != null) {
                        detectedSource = webResult.second!!
                    }
                }
            }

            val finalTitle = customTitle?.trim()?.ifBlank { null }
                ?: resolved.title?.trim()?.ifBlank { null }
                ?: "${detectedSource.displayName} Song"
            val finalArtist = customArtist?.trim()?.ifBlank { null }
                ?: resolved.artist?.trim()?.ifBlank { null }
                ?: "${detectedSource.displayName} Artist"
            val resolvedCoverUrl = resolved.coverUrl
            val resolvedDurationSec = resolved.durationSeconds.coerceAtLeast(10)
            val mediaStreamUrl = resolved.streamUrl

            val dlItem = DownloadItem(
                trackId = trackId,
                trackTitle = finalTitle,
                artist = finalArtist,
                isVideo = isVideo,
                progress = 0f,
                status = DownloadStatus.DOWNLOADING,
                sourceUrl = cleanUrl,
                sizeText = if (isVideo) "HD Video (MP4)" else "HQ Audio"
            )
            _downloads.update { listOf(dlItem) + it }

            val rawName = finalTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "").trim().take(35).ifBlank { "ASD_Music" }
            var finalExt = resolved.preferredExt ?: if (isVideo) "mp4" else "mp3"
            var finalMime = resolved.mimeType ?: if (isVideo) "video/mp4" else "audio/mpeg"
            var targetFile = createTargetFile(rawName, trackId, finalExt)

            try {
                var streamSuccess = false
                if (!mediaStreamUrl.isNullOrBlank()) {
                    val reqBuilder = okhttp3.Request.Builder()
                        .url(mediaStreamUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    if (detectedSource == LinkSource.TIKTOK) {
                        reqBuilder.header("Referer", "https://www.tiktok.com/")
                    }

                    okHttpClient.newCall(reqBuilder.build()).execute().use { dlResp ->
                        val respContentType = dlResp.header("Content-Type")?.lowercase() ?: ""
                        // Guard: never save an HTML webpage as an MP3/MP4!
                        if (dlResp.isSuccessful && !respContentType.contains("text/html")) {
                            // Align extension & MIME type with actual audio/video container
                            val (mappedExt, mappedMime) = mapExtensionAndMime(respContentType, mediaStreamUrl, isVideo, finalExt)
                            if (mappedExt != finalExt) {
                                if (targetFile.exists()) targetFile.delete()
                                finalExt = mappedExt
                                finalMime = mappedMime
                                targetFile = createTargetFile(rawName, trackId, finalExt)
                            } else {
                                finalMime = mappedMime
                            }

                            val body = dlResp.body
                            if (body != null) {
                                val totalLength = body.contentLength()
                                body.byteStream().use { input ->
                                    targetFile.outputStream().use { output ->
                                        val buffer = ByteArray(16384)
                                        var bytesRead: Int
                                        var totalRead = 0L
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            totalRead += bytesRead
                                            if (totalLength > 0) {
                                                val progress = (totalRead.toFloat() / totalLength).coerceIn(0f, 0.99f)
                                                _downloads.update { list ->
                                                    list.map { if (it.trackId == trackId) it.copy(progress = progress) else it }
                                                }
                                            }
                                        }
                                    }
                                }
                                streamSuccess = targetFile.exists() && targetFile.length() > 1024
                            }
                        } else if (respContentType.contains("text/html")) {
                            Log.w("MusicRepository", "Stream URL returned HTML instead of media: $mediaStreamUrl")
                        }
                    }
                }

                if (streamSuccess) {
                    // Save cover art locally so picture never disappears upon restart or when offline
                    var localCoverPath: String? = null
                    if (!resolvedCoverUrl.isNullOrBlank()) {
                        try {
                            val coverFile = File(publicMusicDir, "${rawName}_${trackId.takeLast(6)}_cover.jpg").let {
                                if (it.parentFile?.canWrite() == true) it else File(downloadsDir, "${trackId}_cover.jpg")
                            }
                            val imgReq = okhttp3.Request.Builder()
                                .url(resolvedCoverUrl)
                                .header("User-Agent", "Mozilla/5.0")
                                .build()
                            okHttpClient.newCall(imgReq).execute().use { imgResp ->
                                if (imgResp.isSuccessful) {
                                    imgResp.body?.byteStream()?.use { input ->
                                        coverFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    if (coverFile.exists() && coverFile.length() > 100) {
                                        localCoverPath = coverFile.absolutePath
                                        try {
                                            MediaScannerConnection.scanFile(context, arrayOf(coverFile.absolutePath), arrayOf("image/jpeg"), null)
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MusicRepository", "Local cover save: ${e.message}")
                        }
                    }

                    // 1. Scan physical file into Android MediaStore so all device music players immediately see it
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(targetFile.absolutePath),
                            arrayOf(finalMime)
                        ) { path, uri ->
                            Log.d("MusicRepository", "Public device file scanned: $path -> $uri")
                        }
                    } catch (e: Exception) {
                        Log.w("MusicRepository", "MediaScanner error: ${e.message}")
                    }

                    // 2. Also export via ContentResolver MediaStore for Android 10+ Scoped Storage assurance
                    saveToMediaStore(targetFile, finalTitle, finalArtist, isVideo, finalExt, finalMime)

                    val albumTag = if (isVideo) "${detectedSource.displayName} Video" else "${detectedSource.displayName} Audio"
                    val genreTag = when (detectedSource) {
                        LinkSource.YOUTUBE -> "YouTube Stream"
                        LinkSource.TIKTOK -> "TikTok Trend"
                        LinkSource.GOOGLE -> "Google Drive"
                        LinkSource.WEB -> "Web Download"
                    }

                    val readyTrack = Track(
                        id = trackId,
                        title = finalTitle,
                        artist = finalArtist,
                        album = albumTag,
                        durationSeconds = resolvedDurationSec,
                        genre = genreTag,
                        coverUrl = localCoverPath ?: resolvedCoverUrl,
                        isVideo = isVideo,
                        isDownloaded = true,
                        sourceUrl = mediaStreamUrl ?: cleanUrl,
                        localFilePath = targetFile.absolutePath,
                        animatedStyle = AnimatedArtStyle.VINYL
                    )

                    _tracks.update { listOf(readyTrack) + it.filter { t -> t.id != readyTrack.id } }
                    _downloads.update { list ->
                        listOf(
                            DownloadItem(
                                trackId = trackId,
                                trackTitle = finalTitle,
                                artist = finalArtist,
                                isVideo = isVideo,
                                progress = 1.0f,
                                status = DownloadStatus.COMPLETED,
                                filePath = targetFile.absolutePath
                            )
                        ) + list.filter { it.trackId != trackId }
                    }

                    persistDownloadedTracks()
                    onComplete(readyTrack)
                } else {
                    if (targetFile.exists()) targetFile.delete()
                    _downloads.update { list ->
                        list.map { if (it.trackId == trackId) it.copy(status = DownloadStatus.FAILED) else it }
                    }
                    onError("Could not extract playable stream from this ${detectedSource.displayName} link. Try another link or check your internet.")
                }
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                _downloads.update { list ->
                    list.map { if (it.trackId == trackId) it.copy(status = DownloadStatus.FAILED) else it }
                }
                onError("Download error: ${e.message}")
            }
        }
    }

    private fun createTargetFile(rawName: String, trackId: String, ext: String): File {
        val publicTarget = File(publicMusicDir, "${rawName}_${trackId.takeLast(6)}.$ext")
        return try {
            if (!publicMusicDir.exists()) publicMusicDir.mkdirs()
            publicTarget.createNewFile()
            publicTarget
        } catch (_: Exception) {
            File(downloadsDir, "${rawName}_${trackId.takeLast(6)}.$ext")
        }
    }

    private fun mapExtensionAndMime(
        contentType: String,
        url: String,
        isVideo: Boolean,
        defaultExt: String
    ): Pair<String, String> {
        val ct = contentType.lowercase()
        val u = url.lowercase()
        return when {
            isVideo || ct.contains("video/mp4") || u.contains("mime=video%2fmp4") || u.contains(".mp4") ->
                "mp4" to "video/mp4"
            ct.contains("video/webm") -> "webm" to "video/webm"
            ct.contains("audio/mp4") || ct.contains("audio/aac") || u.contains("mime=audio%2fmp4") || u.contains(".m4a") ->
                "m4a" to "audio/mp4"
            ct.contains("audio/mpeg") || ct.contains("audio/mp3") || u.contains(".mp3") ->
                "mp3" to "audio/mpeg"
            ct.contains("audio/ogg") || ct.contains("application/ogg") || u.contains(".ogg") ->
                "ogg" to "audio/ogg"
            ct.contains("audio/wav") || u.contains(".wav") ->
                "wav" to "audio/wav"
            ct.contains("audio/webm") ->
                "webm" to "audio/webm"
            else -> defaultExt to (if (isVideo) "video/mp4" else "audio/mpeg")
        }
    }

    /**
     * Multi-Engine Real YouTube Downloader:
     * 1. YouTube oEmbed for accurate title, channel & thumbnail cover
     * 2. YouTube InnerTube player API (IOS -> ANDROID_VR -> ANDROID -> TVHTML5_SIMPLY_EMBEDDED_PLAYER)
     * 3. Mobile Watch Page ytInitialPlayerResponse JSON extractor
     * 4. Federated Piped & Invidious instances fallback
     */
    private fun resolveYouTubeStream(
        okHttpClient: okhttp3.OkHttpClient,
        ytId: String,
        isVideo: Boolean
    ): ResolvedMedia {
        var resolvedTitle: String? = null
        var resolvedArtist: String? = null
        var resolvedCoverUrl: String? = "https://img.youtube.com/vi/$ytId/hqdefault.jpg"
        var resolvedDurationSec = 180
        var streamUrl: String? = null
        var preferredExt: String? = null
        var mimeType: String? = null

        // 1. Official YouTube oEmbed for instant reliable metadata
        try {
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$ytId&format=json"
            val oembedReq = okhttp3.Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            okHttpClient.newCall(oembedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val oembedJson = JSONObject(resp.body?.string() ?: "{}")
                    resolvedTitle = oembedJson.optString("title").trim().ifBlank { null }
                    resolvedArtist = oembedJson.optString("author_name").trim().ifBlank { null }
                    val thumb = oembedJson.optString("thumbnail_url").trim()
                    if (thumb.isNotBlank()) resolvedCoverUrl = thumb
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "YouTube oEmbed: ${e.message}")
        }

        // 2. YouTube InnerTube native client APIs (returns direct playable URLs!)
        data class InnerTubeClient(
            val clientName: String,
            val clientVersion: String,
            val userAgent: String,
            val deviceMake: String? = null,
            val deviceModel: String? = null,
            val osVersion: String? = null,
            val androidSdk: Int? = null
        )

        val clients = listOf(
            InnerTubeClient(
                clientName = "IOS",
                clientVersion = "19.45.4",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osVersion = "18.1.0.22B83"
            ),
            InnerTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osVersion = "12L",
                androidSdk = 32
            ),
            InnerTubeClient(
                clientName = "ANDROID",
                clientVersion = "19.44.38",
                userAgent = "com.google.android.youtube/19.44.38 (Linux; U; Android 11) gzip",
                osVersion = "11",
                androidSdk = 30
            ),
            InnerTubeClient(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        )

        for (client in clients) {
            if (streamUrl != null) break
            try {
                val clientJson = JSONObject().apply {
                    put("clientName", client.clientName)
                    put("clientVersion", client.clientVersion)
                    put("hl", "en")
                    put("gl", "US")
                    client.deviceMake?.let { put("deviceMake", it) }
                    client.deviceModel?.let { put("deviceModel", it) }
                    client.osVersion?.let { put("osVersion", it) }
                    client.androidSdk?.let { put("androidSdkVersion", it) }
                }
                val contextJson = JSONObject().apply {
                    put("client", clientJson)
                    if (client.clientName == "TVHTML5_SIMPLY_EMBEDDED_PLAYER") {
                        put("thirdParty", JSONObject().apply { put("embedUrl", "https://www.youtube.com/") })
                    }
                }
                val payload = JSONObject().apply {
                    put("videoId", ytId)
                    put("context", contextJson)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }

                val req = okhttp3.Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", client.userAgent)
                    .header("Origin", "https://www.youtube.com")
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val root = JSONObject(resp.body?.string() ?: "{}")
                        val details = root.optJSONObject("videoDetails")
                        if (details != null) {
                            if (resolvedTitle == null) resolvedTitle = details.optString("title").trim().ifBlank { null }
                            if (resolvedArtist == null) resolvedArtist = details.optString("author").trim().ifBlank { null }
                            val len = details.optString("lengthSeconds").toIntOrNull() ?: 0
                            if (len > 0) resolvedDurationSec = len
                        }

                        val streamingData = root.optJSONObject("streamingData")
                        if (streamingData != null) {
                            val picked = pickBestYouTubeStreamingFormat(streamingData, isVideo)
                            if (picked != null) {
                                streamUrl = picked.first
                                preferredExt = picked.second
                                mimeType = picked.third
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "InnerTube (${client.clientName}): ${e.message}")
            }
        }

        // 3. Fallback: Parse ytInitialPlayerResponse from YouTube mobile watch page
        if (streamUrl == null) {
            try {
                val watchReq = okhttp3.Request.Builder()
                    .url("https://m.youtube.com/watch?v=$ytId")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .build()
                okHttpClient.newCall(watchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val marker = "var ytInitialPlayerResponse = "
                        val start = html.indexOf(marker)
                        if (start != -1) {
                            val jsonStart = start + marker.length
                            val end = html.indexOf(";</script>", jsonStart)
                            if (end > jsonStart) {
                                val rawJson = html.substring(jsonStart, end)
                                val root = JSONObject(rawJson)
                                val details = root.optJSONObject("videoDetails")
                                if (details != null) {
                                    if (resolvedTitle == null) resolvedTitle = details.optString("title").trim().ifBlank { null }
                                    if (resolvedArtist == null) resolvedArtist = details.optString("author").trim().ifBlank { null }
                                    val len = details.optString("lengthSeconds").toIntOrNull() ?: 0
                                    if (len > 0) resolvedDurationSec = len
                                }
                                root.optJSONObject("streamingData")?.let { sd ->
                                    val picked = pickBestYouTubeStreamingFormat(sd, isVideo)
                                    if (picked != null) {
                                        streamUrl = picked.first
                                        preferredExt = picked.second
                                        mimeType = picked.third
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "YouTube HTML scrape: ${e.message}")
            }
        }

        // 4. Fallback: Federated Piped & Invidious public instances
        if (streamUrl == null) {
            val pipedHosts = listOf(
                "https://pipedapi.kavin.rocks",
                "https://pipedapi.adminforge.de",
                "https://api.piped.yt",
                "https://pipedapi.reallyaweso.me"
            )
            for (host in pipedHosts) {
                if (streamUrl != null) break
                try {
                    val req = okhttp3.Request.Builder()
                        .url("$host/streams/$ytId")
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    okHttpClient.newCall(req).execute().use { pResp ->
                        if (pResp.isSuccessful) {
                            val pJson = JSONObject(pResp.body?.string() ?: "{}")
                            if (resolvedTitle == null) resolvedTitle = pJson.optString("title").trim().ifBlank { null }
                            if (resolvedArtist == null) resolvedArtist = pJson.optString("uploader").trim().ifBlank { null }
                            val dur = pJson.optInt("duration", 0)
                            if (dur > 0) resolvedDurationSec = dur

                            if (isVideo) {
                                val vStreams = pJson.optJSONArray("videoStreams")
                                if (vStreams != null) {
                                    for (i in 0 until vStreams.length()) {
                                        val s = vStreams.optJSONObject(i) ?: continue
                                        val videoOnly = s.optBoolean("videoOnly", true)
                                        val urlStr = s.optString("url")
                                        if (!videoOnly && urlStr.startsWith("http")) {
                                            streamUrl = urlStr
                                            preferredExt = "mp4"
                                            mimeType = "video/mp4"
                                            break
                                        }
                                    }
                                    if (streamUrl == null && vStreams.length() > 0) {
                                        val first = vStreams.optJSONObject(0)?.optString("url")
                                        if (!first.isNullOrBlank()) {
                                            streamUrl = first
                                            preferredExt = "mp4"
                                            mimeType = "video/mp4"
                                        }
                                    }
                                }
                            } else {
                                val aStreams = pJson.optJSONArray("audioStreams")
                                if (aStreams != null) {
                                    // Prefer m4a/mp4 audio so all Android players play it cleanly
                                    var fallbackUrl: String? = null
                                    for (i in 0 until aStreams.length()) {
                                        val s = aStreams.optJSONObject(i) ?: continue
                                        val urlStr = s.optString("url")
                                        val mime = s.optString("mimeType").lowercase()
                                        if (urlStr.startsWith("http")) {
                                            if (mime.contains("audio/mp4") || mime.contains("m4a")) {
                                                streamUrl = urlStr
                                                preferredExt = "m4a"
                                                mimeType = "audio/mp4"
                                                break
                                            } else if (fallbackUrl == null) {
                                                fallbackUrl = urlStr
                                            }
                                        }
                                    }
                                    if (streamUrl == null && fallbackUrl != null) {
                                        streamUrl = fallbackUrl
                                        preferredExt = "m4a"
                                        mimeType = "audio/mp4"
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MusicRepository", "Piped ($host): ${e.message}")
                }
            }
        }

        if (streamUrl == null) {
            val invidiousHosts = listOf(
                "https://inv.nadeko.net",
                "https://invidious.nerdvpn.de",
                "https://yewtu.be"
            )
            for (host in invidiousHosts) {
                if (streamUrl != null) break
                try {
                    val req = okhttp3.Request.Builder()
                        .url("$host/api/v1/videos/$ytId")
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    okHttpClient.newCall(req).execute().use { iResp ->
                        if (iResp.isSuccessful) {
                            val iJson = JSONObject(iResp.body?.string() ?: "{}")
                            if (resolvedTitle == null) resolvedTitle = iJson.optString("title").trim().ifBlank { null }
                            if (resolvedArtist == null) resolvedArtist = iJson.optString("author").trim().ifBlank { null }
                            val dur = iJson.optInt("lengthSeconds", 0)
                            if (dur > 0) resolvedDurationSec = dur

                            if (isVideo) {
                                val formats = iJson.optJSONArray("formatStreams")
                                if (formats != null && formats.length() > 0) {
                                    val urlStr = formats.optJSONObject(0)?.optString("url")
                                    if (!urlStr.isNullOrBlank()) {
                                        streamUrl = urlStr
                                        preferredExt = "mp4"
                                        mimeType = "video/mp4"
                                    }
                                }
                            } else {
                                val adaptive = iJson.optJSONArray("adaptiveFormats")
                                if (adaptive != null) {
                                    for (i in 0 until adaptive.length()) {
                                        val s = adaptive.optJSONObject(i) ?: continue
                                        val type = s.optString("type").lowercase()
                                        val urlStr = s.optString("url")
                                        if (type.startsWith("audio/mp4") && urlStr.startsWith("http")) {
                                            streamUrl = urlStr
                                            preferredExt = "m4a"
                                            mimeType = "audio/mp4"
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MusicRepository", "Invidious ($host): ${e.message}")
                }
            }
        }

        return ResolvedMedia(
            streamUrl = streamUrl,
            title = resolvedTitle,
            artist = resolvedArtist,
            coverUrl = resolvedCoverUrl,
            durationSeconds = resolvedDurationSec,
            preferredExt = preferredExt ?: if (isVideo) "mp4" else "m4a",
            mimeType = mimeType ?: if (isVideo) "video/mp4" else "audio/mp4"
        )
    }

    /**
     * Picks best direct stream URL (audio/mp4 AAC or muxed video/mp4) from YouTube streamingData.
     */
    private fun pickBestYouTubeStreamingFormat(
        streamingData: JSONObject,
        isVideo: Boolean
    ): Triple<String, String, String>? {
        if (!isVideo) {
            // Audio mode: prefer direct audio/mp4 (itag 140) in adaptiveFormats so it's compact & fast
            val adaptive = streamingData.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                var bestM4aUrl: String? = null
                var bestM4aBitrate = -1
                var fallbackAudioUrl: String? = null
                var fallbackMime = "audio/webm"
                var fallbackExt = "webm"

                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val directUrl = fmt.optString("url")
                    if (!directUrl.startsWith("http")) continue
                    val mime = fmt.optString("mimeType").lowercase()
                    val bitrate = fmt.optInt("bitrate", 0)
                    if (mime.startsWith("audio/mp4")) {
                        if (bitrate > bestM4aBitrate) {
                            bestM4aBitrate = bitrate
                            bestM4aUrl = directUrl
                        }
                    } else if (mime.startsWith("audio/") && fallbackAudioUrl == null) {
                        fallbackAudioUrl = directUrl
                        fallbackMime = mime.substringBefore(";").trim()
                        fallbackExt = if (fallbackMime.contains("ogg")) "ogg" else "webm"
                    }
                }
                if (bestM4aUrl != null) {
                    return Triple(bestM4aUrl, "m4a", "audio/mp4")
                }
                if (fallbackAudioUrl != null) {
                    return Triple(fallbackAudioUrl, fallbackExt, fallbackMime)
                }
            }
        }

        // Video mode (or audio fallback): muxed MP4 formats (contains both video + audio)
        val formats = streamingData.optJSONArray("formats")
        if (formats != null) {
            var bestMp4Url: String? = null
            var bestBitrate = -1
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val directUrl = fmt.optString("url")
                if (!directUrl.startsWith("http")) continue
                val mime = fmt.optString("mimeType").lowercase()
                val bitrate = fmt.optInt("bitrate", 0)
                if (mime.contains("mp4") && bitrate > bestBitrate) {
                    bestBitrate = bitrate
                    bestMp4Url = directUrl
                }
            }
            if (bestMp4Url != null) {
                return Triple(bestMp4Url, "mp4", "video/mp4")
            }
        }
        return null
    }

    /**
     * Real TikTok Downloader (Web Rehydration JSON + TikWM API)
     */
    private fun resolveTikTokStream(
        okHttpClient: okhttp3.OkHttpClient,
        finalUrl: String,
        isVideo: Boolean
    ): ResolvedMedia {
        var mediaStreamUrl: String? = null
        var resolvedTitle: String? = null
        var resolvedArtist: String? = null
        var resolvedCoverUrl: String? = null
        var resolvedDurationSec = 180

        // Method A: Extract real stream from official TikTok Web JSON
        try {
            val pageReq = okhttp3.Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            okHttpClient.newCall(pageReq).execute().use { pageResp ->
                if (pageResp.isSuccessful) {
                    val html = pageResp.body?.string() ?: ""
                    val scriptMatcher = Pattern.compile("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>").matcher(html)
                    if (scriptMatcher.find()) {
                        val jsonStr = scriptMatcher.group(1)
                        val rootJson = JSONObject(jsonStr ?: "{}")
                        val defaultScope = rootJson.optJSONObject("__DEFAULT_SCOPE__")
                        val videoDetail = defaultScope?.optJSONObject("webapp.video-detail")
                        val itemStruct = videoDetail?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                        if (itemStruct != null) {
                            val desc = itemStruct.optString("desc").trim()
                            if (desc.isNotBlank()) resolvedTitle = desc
                            val musicObj = itemStruct.optJSONObject("music")
                            if (musicObj != null) {
                                if (resolvedTitle == null) {
                                    val mTitle = musicObj.optString("title").trim()
                                    if (mTitle.isNotBlank()) resolvedTitle = mTitle
                                }
                                val mAuthor = musicObj.optString("authorName").trim()
                                if (mAuthor.isNotBlank()) resolvedArtist = mAuthor
                                val cover = musicObj.optString("coverLarge").ifBlank { musicObj.optString("coverThumb") }
                                if (cover.isNotBlank()) resolvedCoverUrl = cover
                                val dur = musicObj.optInt("duration", 0)
                                if (dur > 0) resolvedDurationSec = dur
                                if (!isVideo) {
                                    mediaStreamUrl = musicObj.optString("playUrl").ifBlank { null }
                                }
                            }
                            if (isVideo || mediaStreamUrl == null) {
                                val videoObj = itemStruct.optJSONObject("video")
                                val vPlay = videoObj?.optString("playAddr")?.ifBlank { null }
                                if (vPlay != null) {
                                    if (isVideo) mediaStreamUrl = vPlay
                                    else if (mediaStreamUrl == null) mediaStreamUrl = vPlay
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "Direct TikTok HTML scrape: ${e.message}")
        }

        // Method B: Fallback to TikWM API
        if (mediaStreamUrl == null) {
            try {
                val tikwmUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(finalUrl, "UTF-8")
                val apiReq = okhttp3.Request.Builder()
                    .url(tikwmUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                okHttpClient.newCall(apiReq).execute().use { apiResp ->
                    if (apiResp.isSuccessful) {
                        val json = JSONObject(apiResp.body?.string() ?: "{}")
                        if (json.optInt("code") == 0) {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                val t = data.optString("title").trim()
                                if (t.isNotBlank()) resolvedTitle = t
                                val musicInfo = data.optJSONObject("music_info")
                                if (musicInfo != null) {
                                    if (resolvedTitle == null) {
                                        val mt = musicInfo.optString("title").trim()
                                        if (mt.isNotBlank()) resolvedTitle = mt
                                    }
                                    val ma = musicInfo.optString("author").trim()
                                    if (ma.isNotBlank()) resolvedArtist = ma
                                    val dur = musicInfo.optInt("duration", 0)
                                    if (dur > 0) resolvedDurationSec = dur
                                }
                                if (resolvedArtist == null) {
                                    val authorObj = data.optJSONObject("author")
                                    val nick = authorObj?.optString("nickname")?.trim() ?: ""
                                    if (nick.isNotBlank()) resolvedArtist = nick
                                }
                                if (resolvedCoverUrl == null) {
                                    resolvedCoverUrl = data.optString("cover").ifBlank { null }
                                }
                                mediaStreamUrl = if (isVideo) {
                                    data.optString("play").ifBlank { null }
                                } else {
                                    data.optString("music").ifBlank { data.optString("play").ifBlank { null } }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "TikWM API fallback: ${e.message}")
            }
        }

        return ResolvedMedia(
            streamUrl = mediaStreamUrl,
            title = resolvedTitle,
            artist = resolvedArtist,
            coverUrl = resolvedCoverUrl,
            durationSeconds = resolvedDurationSec,
            preferredExt = if (isVideo) "mp4" else "mp3",
            mimeType = if (isVideo) "video/mp4" else "audio/mpeg"
        )
    }

    /**
     * Real Google (Drive / Redirects / Search embeds) & General Web Downloader:
     * - Handles Google Drive large-file virus-warning confirmation HTML forms
     * - Follows Google URL unwraps and delegates if destination is YouTube or TikTok
     * - Probes Content-Type directly for direct media links (MP3, M4A, MP4, WAV, AAC, OGG)
     * - Deep-scrapes HTML pages for og:audio, og:video, <audio>, <video>, <source>, <a href>, JSON media URLs, or embedded YouTube videos
     */
    private fun resolveGoogleOrWebStream(
        okHttpClient: okhttp3.OkHttpClient,
        finalUrl: String,
        isVideo: Boolean
    ): Pair<ResolvedMedia, LinkSource?> {
        // 1. Check Google Drive ID
        val driveFileId = LinkDetector.extractGoogleDriveFileId(finalUrl)
        if (driveFileId != null) {
            var driveTitle: String? = null
            var driveStreamUrl: String? = "https://drive.usercontent.google.com/download?id=$driveFileId&export=download&confirm=t"

            // Probe drive URL to see if it triggers confirmation HTML or directly gives file + Content-Disposition filename
            try {
                val probeReq = okhttp3.Request.Builder()
                    .url("https://drive.google.com/uc?export=download&id=$driveFileId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                okHttpClient.newCall(probeReq).execute().use { resp ->
                    val disp = resp.header("Content-Disposition") ?: ""
                    val fnMatcher = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(disp)
                    if (fnMatcher.find()) {
                        driveTitle = URLDecoder.decode(fnMatcher.group(1), "UTF-8").substringBeforeLast(".")
                    }
                    val ct = resp.header("Content-Type")?.lowercase() ?: ""
                    if (!ct.contains("text/html") && resp.isSuccessful) {
                        driveStreamUrl = resp.request.url.toString()
                    } else if (ct.contains("text/html")) {
                        val html = resp.body?.string() ?: ""
                        // Parse Drive confirmation form
                        val formMatcher = Pattern.compile("action=[\"'](https://drive\\.usercontent\\.google\\.com/download[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                        val actionBase = if (formMatcher.find()) formMatcher.group(1) else "https://drive.usercontent.google.com/download"
                        val uuidMatcher = Pattern.compile("name=[\"']uuid[\"']\\s+value=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                        val uuid = if (uuidMatcher.find()) uuidMatcher.group(1) else null
                        val confirmMatcher = Pattern.compile("name=[\"']confirm[\"']\\s+value=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                        val confirm = if (confirmMatcher.find()) confirmMatcher.group(1) else "t"

                        val nameMatcher = Pattern.compile("<span class=\"uc-name-size\"><a[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE).matcher(html)
                        if (driveTitle == null && nameMatcher.find()) {
                            driveTitle = nameMatcher.group(1)?.trim()?.substringBeforeLast(".")
                        }
                        driveStreamUrl = buildString {
                            append(actionBase)
                            append("?id=").append(driveFileId)
                            append("&export=download")
                            append("&confirm=").append(confirm)
                            if (uuid != null) append("&uuid=").append(uuid)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Google Drive probe: ${e.message}")
            }

            return ResolvedMedia(
                streamUrl = driveStreamUrl,
                title = driveTitle ?: "Google Drive Song",
                artist = "Google Drive",
                preferredExt = if (isVideo) "mp4" else "mp3",
                mimeType = if (isVideo) "video/mp4" else "audio/mpeg"
            ) to LinkSource.GOOGLE
        }

        // 2. Check if direct media URL by extension
        val lower = finalUrl.lowercase()
        val isDirectExt = lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".mp4") ||
            lower.contains(".wav") || lower.contains(".aac") || lower.contains(".ogg") || lower.contains(".webm")
        if (isDirectExt && !lower.contains(".html") && !lower.contains(".php?")) {
            val segment = finalUrl.substringAfterLast("/").substringBefore("?").substringBeforeLast(".")
            val title = try {
                URLDecoder.decode(segment, "UTF-8").replace("_", " ").replace("-", " ").trim().ifBlank { null }
            } catch (_: Exception) {
                segment.ifBlank { null }
            }
            return ResolvedMedia(
                streamUrl = finalUrl,
                title = title,
                artist = "Web Audio"
            ) to null
        }

        // 3. Probe URL over network: if it responds with audio/* or video/* directly, use it; if HTML, deep-scrape media
        var mediaStreamUrl: String? = null
        var resolvedTitle: String? = null
        var resolvedCoverUrl: String? = null
        var redirectedSource: LinkSource? = null

        try {
            val pageReq = okhttp3.Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .build()
            okHttpClient.newCall(pageReq).execute().use { pageResp ->
                val actualUrl = LinkDetector.unwrapGoogleUrl(pageResp.request.url.toString())
                val actualSource = LinkDetector.detectSource(actualUrl)
                if (actualSource == LinkSource.YOUTUBE) {
                    val ytId = LinkDetector.extractYouTubeVideoId(actualUrl)
                    if (ytId != null) {
                        return resolveYouTubeStream(okHttpClient, ytId, isVideo) to LinkSource.YOUTUBE
                    }
                } else if (actualSource == LinkSource.TIKTOK) {
                    return resolveTikTokStream(okHttpClient, actualUrl, isVideo) to LinkSource.TIKTOK
                }

                val ct = pageResp.header("Content-Type")?.lowercase() ?: ""
                if (pageResp.isSuccessful && !ct.contains("text/html")) {
                    // Server returned raw media stream directly
                    val disp = pageResp.header("Content-Disposition") ?: ""
                    val fnMatcher = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE).matcher(disp)
                    if (fnMatcher.find()) {
                        resolvedTitle = URLDecoder.decode(fnMatcher.group(1), "UTF-8").substringBeforeLast(".")
                    } else {
                        val segment = actualUrl.substringAfterLast("/").substringBefore("?").substringBeforeLast(".")
                        resolvedTitle = segment.replace("_", " ").replace("-", " ").trim().ifBlank { null }
                    }
                    mediaStreamUrl = actualUrl
                } else if (pageResp.isSuccessful) {
                    val html = pageResp.body?.string() ?: ""

                    // Page title
                    val tMatcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
                    if (tMatcher.find()) {
                        resolvedTitle = tMatcher.group(1)?.trim()
                            ?.replace(Regex("\\s*[-|]\\s*(YouTube|TikTok|Google).*"), "")
                            ?.take(80)
                    }

                    // OpenGraph cover art
                    val ogImg = Pattern.compile("<meta\\s+(?:property|name)=[\"']og:image[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (ogImg.find()) resolvedCoverUrl = resolveRelativeUrl(actualUrl, ogImg.group(1))

                    // Try meta og:audio / og:video / twitter:player:stream
                    val metaMediaRegex = if (isVideo) {
                        "<meta\\s+(?:property|name)=[\"'](?:og:video(?::url|:secure_url)?|twitter:player:stream)[\"']\\s+content=[\"']([^\"']+)[\"']"
                    } else {
                        "<meta\\s+(?:property|name)=[\"'](?:og:audio(?::url|:secure_url)?|og:video(?::url|:secure_url)?|twitter:player:stream)[\"']\\s+content=[\"']([^\"']+)[\"']"
                    }
                    val metaMatcher = Pattern.compile(metaMediaRegex, Pattern.CASE_INSENSITIVE).matcher(html)
                    while (metaMatcher.find() && mediaStreamUrl == null) {
                        val candidate = metaMatcher.group(1) ?: continue
                        if (!candidate.contains("youtube.com/embed") && (candidate.contains(".mp3") || candidate.contains(".m4a") || candidate.contains(".mp4") || candidate.contains(".wav") || candidate.contains("stream"))) {
                            mediaStreamUrl = resolveRelativeUrl(actualUrl, candidate)
                        }
                    }

                    // Try <audio>, <video>, <source> tags
                    if (mediaStreamUrl == null) {
                        val tagPattern = if (isVideo) {
                            "<(?:video|source)[^>]*src=[\"']([^\"']+\\.(?:mp4|webm|m4v)[^\"']*)[\"']"
                        } else {
                            "<(?:audio|source)[^>]*src=[\"']([^\"']+\\.(?:mp3|m4a|ogg|wav|aac)[^\"']*)[\"']"
                        }
                        val srcMatcher = Pattern.compile(tagPattern, Pattern.CASE_INSENSITIVE).matcher(html)
                        if (srcMatcher.find()) {
                            mediaStreamUrl = resolveRelativeUrl(actualUrl, srcMatcher.group(1))
                        }
                    }

                    // Try direct <a href="...mp3|m4a|mp4"> download links or JSON contentUrl
                    if (mediaStreamUrl == null) {
                        val hrefPattern = if (isVideo) {
                            "(?:href|contentUrl)[\"':=\\s]+(https?:\\\\?/\\\\?/[^\"'\\s<>]+\\.mp4[^\"'\\s<>]*)"
                        } else {
                            "(?:href|contentUrl)[\"':=\\s]+(https?:\\\\?/\\\\?/[^\"'\\s<>]+\\.(?:mp3|m4a|aac|wav|ogg)[^\"'\\s<>]*)"
                        }
                        val hrefMatcher = Pattern.compile(hrefPattern, Pattern.CASE_INSENSITIVE).matcher(html)
                        if (hrefMatcher.find()) {
                            mediaStreamUrl = hrefMatcher.group(1)?.replace("\\/", "/")?.replace("\\u0026", "&")
                        }
                    }

                    // If page (or Google Search page!) embeds a YouTube link, extract and download that YouTube track!
                    if (mediaStreamUrl == null) {
                        val ytEmbedMatcher = Pattern.compile(
                            "https?:\\/\\/(?:www\\.|m\\.)?(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})",
                            Pattern.CASE_INSENSITIVE
                        ).matcher(html)
                        if (ytEmbedMatcher.find()) {
                            val embeddedYtId = ytEmbedMatcher.group(1)
                            if (embeddedYtId != null) {
                                return resolveYouTubeStream(okHttpClient, embeddedYtId, isVideo) to LinkSource.YOUTUBE
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "Google/Web resolution error: ${e.message}")
        }

        return ResolvedMedia(
            streamUrl = mediaStreamUrl,
            title = resolvedTitle,
            coverUrl = resolvedCoverUrl
        ) to redirectedSource
    }

    private fun resolveRelativeUrl(baseUrl: String, candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        return try {
            URI(baseUrl).resolve(candidate.trim()).toString()
        } catch (_: Exception) {
            candidate.trim()
        }
    }

    private fun persistDownloadedTracks() {
        try {
            val downloadedTracks = _tracks.value.filter { it.isDownloaded && !it.localFilePath.isNullOrBlank() }
            val arr = JSONArray()
            for (track in downloadedTracks) {
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("durationSeconds", track.durationSeconds)
                    put("genre", track.genre)
                    put("coverUrl", track.coverUrl ?: "")
                    put("isVideo", track.isVideo)
                    put("sourceUrl", track.sourceUrl ?: "")
                    put("localFilePath", track.localFilePath ?: "")
                    put("playCount", track.playCount)
                }
                arr.put(obj)
            }
            metadataFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w("MusicRepository", "persistDownloadedTracks: ${e.message}")
        }
    }

    private fun saveToMediaStore(
        file: File,
        title: String,
        artist: String,
        isVideo: Boolean,
        ext: String = if (isVideo) "mp4" else "mp3",
        mime: String = if (isVideo) "video/mp4" else "audio/mpeg"
    ) {
        try {
            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "").take(50).ifBlank { "Song" }

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$sanitizedTitle.$ext")
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.ARTIST, artist)
                put(MediaStore.MediaColumns.ALBUM, "ASD Music")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        if (isVideo) Environment.DIRECTORY_MOVIES + "/ASDMusic" else Environment.DIRECTORY_MUSIC + "/ASDMusic"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            }

            val uri = context.contentResolver.insert(collection, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                Log.d("MusicRepository", "Saved copy to device public MediaStore: $uri")
            }
        } catch (e: Exception) {
            Log.w("MusicRepository", "saveToMediaStore: ${e.message}")
        }
    }

    fun removeTrack(trackId: String) {
        _tracks.update { it.filter { t -> t.id != trackId } }
        _downloads.update { it.filter { d -> d.trackId != trackId } }
        persistDownloadedTracks()
        val f1 = File(downloadsDir, "${trackId}_audio.mp3")
        if (f1.exists()) f1.delete()
        val f2 = File(downloadsDir, "${trackId}_audio.mp4")
        if (f2.exists()) f2.delete()
        val f3 = File(downloadsDir, "${trackId}_cover.jpg")
        if (f3.exists()) f3.delete()
    }

    fun toggleFavorite(trackId: String) {
        _favoriteTrackIds.update { set ->
            if (set.contains(trackId)) set - trackId else set + trackId
        }
    }

    fun updateProfile(name: String, bio: String) {
        _userProfile.update { it.copy(name = name, bio = bio) }
    }
}
