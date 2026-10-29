package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MusicPlaybackService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var lastTrackId: String? = null
    private var lastIsPlaying: Boolean? = null

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Ensure service is immediately foregrounded with fallback notification
        val state = PlaybackManager.playbackState.value
        val track = state.currentTrack
        lastTrackId = track?.id
        lastIsPlaying = state.isPlaying
        val initialNotification = buildNotification(
            title = track?.title ?: "ASD MUSIC",
            artist = track?.artist ?: "Background Playback Active",
            isPlaying = state.isPlaying
        )
        startForegroundSafely(initialNotification)

        // Observe playback state changes to refresh notification ONLY when track or playing state changes
        serviceScope.launch {
            PlaybackManager.playbackState.collectLatest { curState ->
                val curTrack = curState.currentTrack
                if (curTrack != null) {
                    if (curTrack.id != lastTrackId || curState.isPlaying != lastIsPlaying) {
                        lastTrackId = curTrack.id
                        lastIsPlaying = curState.isPlaying
                        val notification = buildNotification(
                            title = curTrack.title,
                            artist = curTrack.artist,
                            isPlaying = curState.isPlaying
                        )
                        startForegroundSafely(notification)
                    }
                } else {
                    lastTrackId = null
                    lastIsPlaying = null
                    try {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } catch (_: Exception) {}
                }
            }
        }
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
            val notification = buildNotification(
                title = track.title,
                artist = track.artist,
                isPlaying = curState.isPlaying
            )
            startForegroundSafely(notification)
        }

        when (intent?.action) {
            ACTION_PLAY -> PlaybackManager.play(this)
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
                // Handled reactively
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ASD MUSIC Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Seamless background playback controls and notification"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): Notification {
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
        val prevPending = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY }
        val playPausePending = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, MusicPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("$artist • ASD MUSIC")
            .setSubText("Now Playing")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevPending)
            .addAction(playPauseIcon, playPauseTitle, playPausePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
