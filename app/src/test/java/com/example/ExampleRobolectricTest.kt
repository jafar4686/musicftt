package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicRepository
import com.example.model.EqualizerPreset
import com.example.model.LarkSoundSettings
import com.example.service.PlaybackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ASD MUSIC", appName)
  }

  @Test
  fun `repository loads tracks and downloads from link`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = MusicRepository(context)

    assertTrue(repository.tracks.value.isNotEmpty())

    repository.addAndDownloadFromLink(
      url = "https://tiktok.com/@creator/video/123",
      customTitle = "My TikTok Track",
      customArtist = "Creator",
      isVideo = false,
      onComplete = { track ->
        assertNotNull(track)
        assertEquals("My TikTok Track", track.title)
      }
    )

    repository.addAndDownloadFromLink(
      url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      customTitle = "Never Gonna Give You Up",
      customArtist = "Rick Astley",
      isVideo = false,
      onComplete = { track ->
        assertNotNull(track)
        assertEquals("Never Gonna Give You Up", track.title)
        assertEquals("YouTube Stream", track.genre)
      }
    )
  }

  @Test
  fun `lark sound settings update correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    PlaybackManager.initialize(context)

    PlaybackManager.setEqualizerPreset(EqualizerPreset.ROCK)
    assertEquals(EqualizerPreset.ROCK, PlaybackManager.soundSettings.value.selectedPreset)

    PlaybackManager.setBassBoost(80)
    assertEquals(80, PlaybackManager.soundSettings.value.bassBoostPercent)

    PlaybackManager.setVirtualizer(50)
    assertEquals(50, PlaybackManager.soundSettings.value.virtualizer3DPercent)

    PlaybackManager.setPlaybackSpeed(1.25f)
    assertEquals(1.25f, PlaybackManager.soundSettings.value.playbackSpeed, 0.01f)
  }

  @Test
  fun `sequential playback plays tracks one after the other in order`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    PlaybackManager.initialize(context)

    val sampleList = (1..15).map { num ->
      com.example.model.Track(
        id = "track_$num",
        title = "Song Number $num",
        artist = "Artist $num",
        durationSeconds = 180
      )
    }

    // Play song number 10
    val songTen = sampleList[9]
    PlaybackManager.playTrack(songTen, sampleList, context)
    assertEquals("track_10", PlaybackManager.playbackState.value.currentTrack?.id)

    // Trigger next - MUST be song number 11 (index 10), NEVER song number 8
    PlaybackManager.next(context)
    assertEquals("track_11", PlaybackManager.playbackState.value.currentTrack?.id)

    // Trigger next again - MUST be song number 12
    PlaybackManager.next(context)
    assertEquals("track_12", PlaybackManager.playbackState.value.currentTrack?.id)
  }

  @Test
  fun `automatic link detection recognizes youtube tiktok google and web without selection`() {
    assertEquals(
      com.example.data.LinkSource.YOUTUBE,
      com.example.data.LinkDetector.detectSource("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    )
    assertEquals(
      com.example.data.LinkSource.YOUTUBE,
      com.example.data.LinkDetector.detectSource("youtu.be/kJQP7kiw5Fk")
    )
    assertEquals(
      "kJQP7kiw5Fk",
      com.example.data.LinkDetector.extractYouTubeVideoId("https://youtube.com/shorts/kJQP7kiw5Fk?feature=share")
    )
    assertEquals(
      com.example.data.LinkSource.TIKTOK,
      com.example.data.LinkDetector.detectSource("https://vm.tiktok.com/ZM6abc123/")
    )
    assertEquals(
      com.example.data.LinkSource.GOOGLE,
      com.example.data.LinkDetector.detectSource("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view")
    )
    assertEquals(
      "1AbCdEfGhIjKlMnOpQrStUvWxYz",
      com.example.data.LinkDetector.extractGoogleDriveFileId("https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view")
    )
    // Google search redirect wrapping YouTube URL should automatically unwrap and detect YouTube!
    assertEquals(
      com.example.data.LinkSource.YOUTUBE,
      com.example.data.LinkDetector.detectSource("https://www.google.com/url?sa=t&url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ")
    )
    assertEquals(
      com.example.data.LinkSource.WEB,
      com.example.data.LinkDetector.detectSource("https://example.com/audio/song.mp3")
    )
  }

  @Test
  fun `play count increments only on full beginning-to-end playback and rejects advancing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = MusicRepository(context)
    PlaybackManager.initialize(context)

    val firstTrack = repository.tracks.value.first()
    val initialCount = firstTrack.playCount
    var recordedId: String? = null
    PlaybackManager.setOnValidTrackPlayedListener { trackId ->
      recordedId = trackId
      repository.incrementPlayCount(trackId)
    }

    // 1. Play cleanly from 0 to 10_000ms in 500ms steps -> MUST count
    val durationMs = 10_000L
    PlaybackManager.playTrack(firstTrack.copy(durationSeconds = 10), listOf(firstTrack), context)
    for (pos in 500L..10_000L step 500L) {
      PlaybackManager.recordPlaybackProgress(pos, durationMs, 500L)
    }
    assertTrue(PlaybackManager.evaluateValidCompletionAndNotify(durationMs))
    assertEquals(firstTrack.id, recordedId)
    assertEquals(initialCount + 1, repository.tracks.value.first { it.id == firstTrack.id }.playCount)

    // 2. Play from 0 to 1_000ms, then seek forward ("in advance") to 9_000ms and finish -> MUST NOT count
    PlaybackManager.playTrack(firstTrack.copy(durationSeconds = 10), listOf(firstTrack), context)
    PlaybackManager.recordPlaybackProgress(500L, durationMs, 500L)
    PlaybackManager.recordPlaybackProgress(1_000L, durationMs, 500L)
    PlaybackManager.seekTo(9_000L)
    PlaybackManager.recordPlaybackProgress(9_500L, durationMs, 500L)
    PlaybackManager.recordPlaybackProgress(10_000L, durationMs, 500L)
    assertEquals(false, PlaybackManager.evaluateValidCompletionAndNotify(durationMs))
    assertEquals(initialCount + 1, repository.tracks.value.first { it.id == firstTrack.id }.playCount)

    // 3. After advancing, seek back to 0ms (restart from beginning) and play cleanly to end -> MUST count
    PlaybackManager.seekTo(0L)
    for (pos in 500L..10_000L step 500L) {
      PlaybackManager.recordPlaybackProgress(pos, durationMs, 500L)
    }
    assertTrue(PlaybackManager.evaluateValidCompletionAndNotify(durationMs))
    assertEquals(initialCount + 2, repository.tracks.value.first { it.id == firstTrack.id }.playCount)

    PlaybackManager.setOnValidTrackPlayedListener(null)
  }

  @Test
  fun `theme mode persists across day morning and dark modes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = MusicRepository(context)

    repository.saveManualThemeMode(com.example.model.AppThemeMode.MORNING)
    assertEquals(com.example.model.AppThemeMode.MORNING, repository.loadManualThemeMode())

    repository.saveManualThemeMode(com.example.model.AppThemeMode.DARK)
    assertEquals(com.example.model.AppThemeMode.DARK, repository.loadManualThemeMode())

    repository.saveManualThemeMode(com.example.model.AppThemeMode.DAY)
    assertEquals(com.example.model.AppThemeMode.DAY, repository.loadManualThemeMode())
  }

  @Test
  fun `shared text payload extracts clean tiktok and youtube urls`() {
    val tiktokShare = "Check out this cool track! https://vm.tiktok.com/ZM6abc123/ #music #fyp"
    val extractedTikTok = com.example.data.LinkDetector.extractFirstUrl(tiktokShare)
    assertEquals("https://vm.tiktok.com/ZM6abc123/", extractedTikTok)
    assertEquals(com.example.data.LinkSource.TIKTOK, com.example.data.LinkDetector.detectSource(extractedTikTok!!))

    val youtubeShare = "Listen to this: https://youtu.be/dQw4w9WgXcQ?si=test"
    val extractedYt = com.example.data.LinkDetector.extractFirstUrl(youtubeShare)
    assertEquals("https://youtu.be/dQw4w9WgXcQ?si=test", extractedYt)
    assertEquals(com.example.data.LinkSource.YOUTUBE, com.example.data.LinkDetector.detectSource(extractedYt!!))
  }
}
