package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.MainActivity
import com.example.R
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    private var lastTrackId: String? = null
    private var lastIsPlaying: Boolean? = null
    private var lastReportedPosMs: Long = -1L
    private var lastPositionSyncWallMs: Long = 0L

    private var cachedBitmapTrackId: String? = null
    private var cachedAlbumArtBitmap: Bitmap? = null
    private var artLoadJob: Job? = null

    companion object {
        const val CHANNEL_ID = "asd_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.asd.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.asd.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.example.asd.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.example.asd.ACTION_NEXT"
        const val ACTION_PREV = "com.example.asd.ACTION_PREV"
        const val ACTION_STOP = "com.example.asd.ACTION_STOP"
        const val ACTION_UPDATE_NOTIFICATION = "com.example.asd.ACTION_UPDATE_NOTIFICATION"

        private const val SUPPORTED_ACTIONS =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()

        val state = PlaybackManager.playbackState.value
        val track = state.currentTrack
        lastTrackId = track?.id
        lastIsPlaying = state.isPlaying
        lastReportedPosMs = state.currentPositionMs
        lastPositionSyncWallMs = System.currentTimeMillis()

        val fallbackLogo = loadFallbackLogoBitmap()
        if (track != null) {
            updateMediaSessionMetadata(track, state.durationMs, fallbackLogo)
            updateMediaSessionPlaybackState(state)
            loadTrackArtworkAsync(track, state.durationMs)
        }

        val initialNotification = buildMediaStyleNotification(
            title = track?.title ?: getString(R.string.app_name),
            artist = track?.artist ?: "Ready to play",
            isPlaying = state.isPlaying,
            albumArt = fallbackLogo
        )
        startForegroundSafely(initialNotification)

        serviceScope.launch {
            PlaybackManager.playbackState.collectLatest { curState ->
                val curTrack = curState.currentTrack
                if (curTrack != null) {
                    val trackChanged = curTrack.id != lastTrackId
                    val playStateChanged = curState.isPlaying != lastIsPlaying
                    val now = System.currentTimeMillis()
                    val posDelta = kotlin.math.abs(curState.currentPositionMs - lastReportedPosMs)
                    val timeElapsed = now - lastPositionSyncWallMs >= 1000L

                    if (trackChanged) {
                        lastTrackId = curTrack.id
                        lastIsPlaying = curState.isPlaying
                        lastReportedPosMs = curState.currentPositionMs
                        lastPositionSyncWallMs = now

                        val art = if (cachedBitmapTrackId == curTrack.id && cachedAlbumArtBitmap != null) {
                            cachedAlbumArtBitmap
                        } else {
                            fallbackLogo
                        }
                        updateMediaSessionMetadata(curTrack, curState.durationMs, art)
                        updateMediaSessionPlaybackState(curState)
                        startForegroundSafely(
                            buildMediaStyleNotification(
                                title = curTrack.title,
                                artist = curTrack.artist,
                                isPlaying = curState.isPlaying,
                                albumArt = art
                            )
                        )
                        loadTrackArtworkAsync(curTrack, curState.durationMs)
                    } else if (playStateChanged) {
                        lastIsPlaying = curState.isPlaying
                        lastReportedPosMs = curState.currentPositionMs
                        lastPositionSyncWallMs = now

                        val art = if (cachedBitmapTrackId == curTrack.id && cachedAlbumArtBitmap != null) {
                            cachedAlbumArtBitmap
                        } else {
                            fallbackLogo
                        }
                        updateMediaSessionPlaybackState(curState)
                        startForegroundSafely(
                            buildMediaStyleNotification(
                                title = curTrack.title,
                                artist = curTrack.artist,
                                isPlaying = curState.isPlaying,
                                albumArt = art
                            )
                        )
                    } else if (timeElapsed || posDelta > 2000L) {
                        lastReportedPosMs = curState.currentPositionMs
                        lastPositionSyncWallMs = now
                        updateMediaSessionPlaybackState(curState)
                    }
                } else {
                    lastTrackId = null
                    lastIsPlaying = null
                    mediaSession?.isActive = false
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun initMediaSession() {
        try {
            val sessionIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val sessionActivityPending = PendingIntent.getActivity(
                this,
                10,
                sessionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val session = MediaSession(this, "ASDMusicSession").apply {
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setSessionActivity(sessionActivityPending)
                setCallback(
                    object : MediaSession.Callback() {
                        override fun onPlay() {
                            if (!PlaybackManager.playbackState.value.isPlaying) {
                                PlaybackManager.togglePlayPause(this@MusicPlaybackService)
                            }
                        }

                        override fun onPause() {
                            PlaybackManager.pause(this@MusicPlaybackService)
                        }

                        override fun onSkipToNext() {
                            PlaybackManager.next(this@MusicPlaybackService)
                        }

                        override fun onSkipToPrevious() {
                            PlaybackManager.previous(this@MusicPlaybackService)
                        }

                        override fun onSeekTo(pos: Long) {
                            PlaybackManager.seekTo(pos)
                        }

                        override fun onStop() {
                            PlaybackManager.pause(this@MusicPlaybackService)
                            try {
                                stopForeground(STOP_FOREGROUND_REMOVE)
                            } catch (_: Exception) {}
                            stopSelf()
                        }
                    },
                    Handler(Looper.getMainLooper())
                )
                isActive = true
            }
            mediaSession = session
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "MediaSession init fallback: ${e.message}")
        }
    }

    private fun updateMediaSessionMetadata(track: Track, durationMs: Long, albumArt: Bitmap?) {
        val session = mediaSession ?: return
        try {
            val effectiveDuration = if (durationMs > 0L) durationMs else (track.durationSeconds * 1000L)
            val builder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album.ifBlank { getString(R.string.app_name) })
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, getString(R.string.app_name))
                .putLong(MediaMetadata.METADATA_KEY_DURATION, effectiveDuration)
            if (albumArt != null) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                builder.putBitmap(MediaMetadata.METADATA_KEY_ART, albumArt)
            }
            session.setMetadata(builder.build())
            session.isActive = true
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "Metadata update error: ${e.message}")
        }
    }

    private fun updateMediaSessionPlaybackState(curState: com.example.model.PlaybackState) {
        val session = mediaSession ?: return
        try {
            val stateCode = when {
                curState.isBuffering -> PlaybackState.STATE_BUFFERING
                curState.isPlaying -> PlaybackState.STATE_PLAYING
                else -> PlaybackState.STATE_PAUSED
            }
            val speed = if (curState.isPlaying) {
                PlaybackManager.soundSettings.value.playbackSpeed.coerceIn(0.25f, 3.0f)
            } else {
                0f
            }
            val pbState = PlaybackState.Builder()
                .setActions(SUPPORTED_ACTIONS)
                .setState(stateCode, curState.currentPositionMs.coerceAtLeast(0L), speed)
                .build()
            session.setPlaybackState(pbState)
            session.isActive = curState.currentTrack != null
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "PlaybackState update error: ${e.message}")
        }
    }

    private fun loadTrackArtworkAsync(track: Track, durationMs: Long) {
        artLoadJob?.cancel()
        if (cachedBitmapTrackId == track.id && cachedAlbumArtBitmap != null) return
        val coverUrl = track.coverUrl ?: return
        artLoadJob = serviceScope.launch {
            val loadedBitmap = withContext(Dispatchers.IO) {
                decodeCoverBitmap(coverUrl)
            }
            if (loadedBitmap != null && PlaybackManager.playbackState.value.currentTrack?.id == track.id) {
                cachedBitmapTrackId = track.id
                cachedAlbumArtBitmap = loadedBitmap
                val curState = PlaybackManager.playbackState.value
                updateMediaSessionMetadata(track, durationMs, loadedBitmap)
                startForegroundSafely(
                    buildMediaStyleNotification(
                        title = track.title,
                        artist = track.artist,
                        isPlaying = curState.isPlaying,
                        albumArt = loadedBitmap
                    )
                )
            }
        }
    }

    private fun decodeCoverBitmap(coverUrl: String): Bitmap? {
        return try {
            val raw: Bitmap? = when {
                coverUrl.startsWith("http://") || coverUrl.startsWith("https://") -> {
                    val conn = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = true
                    }
                    conn.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                coverUrl.startsWith("content://") -> {
                    contentResolver.openInputStream(Uri.parse(coverUrl))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                else -> {
                    val f = File(coverUrl)
                    if (f.exists() && f.length() > 0) {
                        BitmapFactory.decodeFile(f.absolutePath)
                    } else null
                }
            }
            raw?.let { scaleBitmapSafely(it, 384) }
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "Cover bitmap decode skipped: ${e.message}")
            null
        }
    }

    private fun loadFallbackLogoBitmap(): Bitmap? {
        return try {
            val decoded = BitmapFactory.decodeResource(resources, R.drawable.asd_music_logo)
            decoded?.let { scaleBitmapSafely(it, 384) }
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmapSafely(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w("MusicPlaybackService", "startForeground safely caught: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val curState = PlaybackManager.playbackState.value
        val track = curState.currentTrack
        if (track != null && (track.id != lastTrackId || curState.isPlaying != lastIsPlaying)) {
            lastTrackId = track.id
            lastIsPlaying = curState.isPlaying
            val art = if (cachedBitmapTrackId == track.id && cachedAlbumArtBitmap != null) {
                cachedAlbumArtBitmap
            } else {
                loadFallbackLogoBitmap()
            }
            updateMediaSessionMetadata(track, curState.durationMs, art)
            updateMediaSessionPlaybackState(curState)
            startForegroundSafely(
                buildMediaStyleNotification(
                    title = track.title,
                    artist = track.artist,
                    isPlaying = curState.isPlaying,
                    albumArt = art
                )
            )
        }

        when (intent?.action) {
            ACTION_PLAY -> if (!curState.isPlaying) PlaybackManager.togglePlayPause(this)
            ACTION_PAUSE -> PlaybackManager.pause(this)
            ACTION_TOGGLE_PLAY -> PlaybackManager.togglePlayPause(this)
            ACTION_NEXT -> PlaybackManager.next(this)
            ACTION_PREV -> PlaybackManager.previous(this)
            ACTION_STOP -> {
                PlaybackManager.pause(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE_NOTIFICATION -> {
                // Handled reactively above
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ASD MUSIC Media Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "System media controls with track progress and artwork"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildMediaStyleNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        albumArt: Bitmap?
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_PREV }
        val prevPending = PendingIntent.getService(
            this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY }
        val playPausePending = PendingIntent.getService(
            this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(
            this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val prevAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            "Previous",
            prevPending
        ).build()

        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(this, playPauseIconRes),
            playPauseTitle,
            playPausePending
        ).build()

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_next),
            "Next",
            nextPending
        ).build()

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopPending
        ).build()

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { token ->
            mediaStyle.setMediaSession(token)
        }

        builder
            .setSmallIcon(R.drawable.ic_music_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(getString(R.string.app_name))
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPending)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(mediaStyle)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)

        if (albumArt != null) {
            builder.setLargeIcon(albumArt)
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        artLoadJob?.cancel()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
        } catch (_: Exception) {}
        mediaSession = null
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
