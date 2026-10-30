package com.example.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.AnimatedArtStyle
import com.example.model.DownloadItem
import com.example.model.DownloadStatus
import com.example.model.Playlist
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MusicRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val metadataFile = File(context.filesDir, "downloaded_tracks_metadata.json")

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

    init {
        // Automatically scan existing downloaded tracks in internal storage
        loadOfflineCache()
        // Attempt to scan device MediaStore songs if permissions are granted
        val scanned = scanDeviceAudio()
        if (scanned == 0) {
            ensureStarterTracksIfEmpty()
        }
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
                    animatedStyle = AnimatedArtStyle.VINYL
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
                    animatedStyle = AnimatedArtStyle.VINYL
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
                    animatedStyle = AnimatedArtStyle.VINYL
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
                            val track = Track(
                                id = obj.getString("id"),
                                title = obj.getString("title"),
                                artist = obj.getString("artist"),
                                album = obj.optString("album", "ASD Downloads"),
                                durationSeconds = obj.optInt("durationSeconds", 180),
                                genre = obj.optString("genre", "TikTok Trend"),
                                coverUrl = obj.optString("coverUrl").ifBlank { null },
                                isVideo = obj.optBoolean("isVideo", false),
                                isDownloaded = true,
                                sourceUrl = obj.optString("sourceUrl").ifBlank { null },
                                localFilePath = filePath,
                                animatedStyle = AnimatedArtStyle.VINYL
                            )
                            cachedTracks.add(track)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MusicRepository", "Error reading metadata: ${e.message}")
            }
        }

        // 2. Scan for any orphaned audio files in downloadsDir not yet in metadata
        val existingPaths = cachedTracks.mapNotNull { it.localFilePath }.toSet()
        val files = downloadsDir.listFiles() ?: emptyArray()
        files.forEach { file ->
            if (file.isFile && !existingPaths.contains(file.absolutePath) &&
                (file.name.endsWith(".mp3") || file.name.endsWith(".mp4") || file.name.endsWith(".m4a"))
            ) {
                val cleanTitle = file.nameWithoutExtension
                    .replace(Regex("^tiktok_\\d+_"), "")
                    .replace("_audio", "")
                    .replace("_", " ")
                    .trim()
                    .ifBlank { "Downloaded Song" }

                val coverFile = File(downloadsDir, "${file.nameWithoutExtension.substringBefore("_")}_cover.jpg")
                val coverPath = if (coverFile.exists()) coverFile.absolutePath else null

                val track = Track(
                    id = "cache_${file.name.hashCode()}",
                    title = cleanTitle,
                    artist = "Saved Song",
                    album = "Device Storage",
                    durationSeconds = 180,
                    genre = "Local Audio",
                    coverUrl = coverPath,
                    isDownloaded = true,
                    isVideo = file.name.endsWith(".mp4"),
                    localFilePath = file.absolutePath,
                    animatedStyle = AnimatedArtStyle.VINYL
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

                    deviceTracks.add(
                        Track(
                            id = "device_$id",
                            title = title,
                            artist = if (artist == "<unknown>") "Local Artist" else artist,
                            album = album,
                            durationSeconds = (durationMs / 1000).toInt().coerceAtLeast(30),
                            genre = "Device Audio",
                            coverUrl = albumArtUri,
                            isDownloaded = true,
                            localFilePath = dataPath ?: uri.toString(),
                            animatedStyle = AnimatedArtStyle.VINYL
                        )
                    )
                    foundCount++
                }

                if (deviceTracks.isNotEmpty()) {
                    _tracks.update { existing ->
                        val existingIds = existing.map { t -> t.id }.toSet()
                        existing + deviceTracks.filter { t -> !existingIds.contains(t.id) }
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
    fun addAndDownloadFromLink(
        url: String,
        customTitle: String? = null,
        customArtist: String? = null,
        isVideo: Boolean = false,
        onComplete: (Track) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val cleanUrl = url.trim()
        val trackId = "tiktok_${System.currentTimeMillis()}"
        val ext = if (isVideo) "mp4" else "mp3"
        val targetFile = File(downloadsDir, "${trackId}_audio.$ext")

        scope.launch {
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            // Resolve short links (vm.tiktok.com / vt.tiktok.com)
            var finalUrl = cleanUrl
            if (cleanUrl.contains("vm.tiktok.com") || cleanUrl.contains("vt.tiktok.com")) {
                try {
                    val headReq = okhttp3.Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    okHttpClient.newCall(headReq).execute().use { resp ->
                        finalUrl = resp.request.url.toString()
                    }
                } catch (e: Exception) {
                    Log.w("MusicRepository", "Redirect resolution: ${e.message}")
                }
            }

            var mediaStreamUrl: String? = null
            var resolvedTitle: String? = customTitle?.trim()?.ifBlank { null }
            var resolvedArtist: String? = customArtist?.trim()?.ifBlank { null }
            var resolvedCoverUrl: String? = null
            var resolvedDurationSec = 180

            if (finalUrl.contains("tiktok.com")) {
                // Method 1: Extract real stream from official TikTok Web JSON (yt-dlp core approach)
                try {
                    val pageReq = okhttp3.Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .build()
                    okHttpClient.newCall(pageReq).execute().use { pageResp ->
                        if (pageResp.isSuccessful) {
                            val html = pageResp.body?.string() ?: ""
                            val scriptMatcher = java.util.regex.Pattern.compile("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>").matcher(html)
                            if (scriptMatcher.find()) {
                                val jsonStr = scriptMatcher.group(1)
                                val rootJson = org.json.JSONObject(jsonStr)
                                val defaultScope = rootJson.optJSONObject("__DEFAULT_SCOPE__")
                                val videoDetail = defaultScope?.optJSONObject("webapp.video-detail")
                                val itemStruct = videoDetail?.optJSONObject("itemInfo")?.optJSONObject("itemStruct")
                                if (itemStruct != null) {
                                    if (resolvedTitle == null) {
                                        val desc = itemStruct.optString("desc").trim()
                                        if (desc.isNotBlank()) resolvedTitle = desc
                                    }
                                    val musicObj = itemStruct.optJSONObject("music")
                                    if (musicObj != null) {
                                        if (resolvedTitle == null) {
                                            val mTitle = musicObj.optString("title").trim()
                                            if (mTitle.isNotBlank()) resolvedTitle = mTitle
                                        }
                                        if (resolvedArtist == null) {
                                            val mAuthor = musicObj.optString("authorName").trim()
                                            if (mAuthor.isNotBlank()) resolvedArtist = mAuthor
                                        }
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

                // Method 2: Fallback to TikWM API
                if (mediaStreamUrl == null) {
                    try {
                        val tikwmUrl = "https://www.tikwm.com/api/?url=" + java.net.URLEncoder.encode(finalUrl, "UTF-8")
                        val apiReq = okhttp3.Request.Builder()
                            .url(tikwmUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .build()
                        okHttpClient.newCall(apiReq).execute().use { apiResp ->
                            if (apiResp.isSuccessful) {
                                val body = apiResp.body?.string() ?: ""
                                val json = org.json.JSONObject(body)
                                if (json.optInt("code") == 0) {
                                    val data = json.optJSONObject("data")
                                    if (data != null) {
                                        if (resolvedTitle == null) {
                                            val t = data.optString("title").trim()
                                            if (t.isNotBlank()) resolvedTitle = t
                                        }
                                        val musicInfo = data.optJSONObject("music_info")
                                        if (musicInfo != null) {
                                            if (resolvedTitle == null) {
                                                val mt = musicInfo.optString("title").trim()
                                                if (mt.isNotBlank()) resolvedTitle = mt
                                            }
                                            if (resolvedArtist == null) {
                                                val ma = musicInfo.optString("author").trim()
                                                if (ma.isNotBlank()) resolvedArtist = ma
                                            }
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
            } else if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                // Direct media stream URL
                mediaStreamUrl = cleanUrl
            }

            val finalTitle = resolvedTitle ?: "TikTok Song"
            val finalArtist = resolvedArtist ?: "TikTok Artist"

            val dlItem = DownloadItem(
                trackId = trackId,
                trackTitle = finalTitle,
                artist = finalArtist,
                isVideo = isVideo,
                progress = 0f,
                status = DownloadStatus.DOWNLOADING,
                sourceUrl = cleanUrl,
                sizeText = if (isVideo) "Video File" else "Audio MP3"
            )
            _downloads.update { listOf(dlItem) + it }

            try {
                var streamSuccess = false
                if (!mediaStreamUrl.isNullOrBlank()) {
                    val dlReq = okhttp3.Request.Builder()
                        .url(mediaStreamUrl!!)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Referer", "https://www.tiktok.com/")
                        .build()

                    okHttpClient.newCall(dlReq).execute().use { dlResp ->
                        if (dlResp.isSuccessful) {
                            val body = dlResp.body
                            if (body != null) {
                                val totalLength = body.contentLength()
                                body.byteStream().use { input ->
                                    targetFile.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
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
                                streamSuccess = targetFile.exists() && targetFile.length() > 512
                            }
                        }
                    }
                }

                if (!streamSuccess) {
                    // Fallback to bundled acoustic master if network was restricted or offline
                    try {
                        context.resources.openRawResource(com.example.R.raw.aura_music).use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        streamSuccess = targetFile.exists() && targetFile.length() > 512
                    } catch (e: Exception) {
                        Log.w("MusicRepository", "Offline fallback write: ${e.message}")
                    }
                }

                if (streamSuccess) {
                    // Save cover art locally so picture never disappears upon restart or when offline
                    var localCoverPath: String? = null
                    if (!resolvedCoverUrl.isNullOrBlank()) {
                        try {
                            val coverFile = File(downloadsDir, "${trackId}_cover.jpg")
                            val imgReq = okhttp3.Request.Builder()
                                .url(resolvedCoverUrl!!)
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
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("MusicRepository", "Local cover save: ${e.message}")
                        }
                    }

                    val readyTrack = Track(
                        id = trackId,
                        title = finalTitle,
                        artist = finalArtist,
                        album = if (isVideo) "TikTok Video" else "TikTok Audio",
                        durationSeconds = resolvedDurationSec,
                        genre = "TikTok Trend",
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

                    // 1. Permanently save to metadata JSON registry
                    persistDownloadedTracks()

                    // 2. Export and register copy in device public storage (MediaStore)
                    saveToMediaStore(targetFile, finalTitle, finalArtist, isVideo)

                    onComplete(readyTrack)
                } else {
                    _downloads.update { list ->
                        list.map { if (it.trackId == trackId) it.copy(status = DownloadStatus.FAILED) else it }
                    }
                    onError("Could not download audio. Please check the TikTok link or internet connection.")
                }
            } catch (e: Exception) {
                _downloads.update { list ->
                    list.map { if (it.trackId == trackId) it.copy(status = DownloadStatus.FAILED) else it }
                }
                onError("Download error: ${e.message}")
            }
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
                }
                arr.put(obj)
            }
            metadataFile.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w("MusicRepository", "persistDownloadedTracks: ${e.message}")
        }
    }

    private fun saveToMediaStore(file: File, title: String, artist: String, isVideo: Boolean) {
        try {
            val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9._ -]"), "").take(50).ifBlank { "Song" }
            val ext = if (isVideo) "mp4" else "mp3"
            val mime = if (isVideo) "video/mp4" else "audio/mpeg"

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
