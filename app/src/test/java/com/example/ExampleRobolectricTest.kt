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

  @Test
  fun `network room generates valid 5-digit codes and encodes sync protocol`() {
    repeat(20) {
      val code = com.example.network.RoomProtocol.generateFiveDigitCode()
      assertEquals(5, code.length)
      assertTrue(code.all { it.isDigit() })
      assertTrue(com.example.network.RoomProtocol.isValidRoomCode(code))
    }

    assertEquals(false, com.example.network.RoomProtocol.isValidRoomCode("1234"))
    assertEquals(false, com.example.network.RoomProtocol.isValidRoomCode("12a45"))

    val members = listOf(
      com.example.model.RoomMember(id = "host_1", name = "Pixel (Host)", isHost = true),
      com.example.model.RoomMember(id = "guest_1", name = "Galaxy S24", isHost = false),
      com.example.model.RoomMember(id = "guest_2", name = "OnePlus 12", isHost = false)
    )
    val welcomeJson = org.json.JSONObject(
      com.example.network.RoomProtocol.encodeWelcome(
        code = "54321",
        phase = com.example.model.RoomPhase.LOBBY,
        members = members
      )
    )
    assertEquals("WELCOME", welcomeJson.getString("type"))
    assertEquals("54321", welcomeJson.getString("code"))
    val parsedMembers = com.example.network.RoomProtocol.parseMembersArray(
      welcomeJson.getJSONArray("members")
    )
    assertEquals(3, parsedMembers.size)
    assertEquals("Galaxy S24", parsedMembers[1].name)

    val testTrack = com.example.model.Track(
      id = "sync_t1",
      title = "Sunset Rhythm",
      artist = "Asd Acoustic",
      durationSeconds = 195
    )
    val prepareJson = org.json.JSONObject(
      com.example.network.RoomProtocol.encodePrepareTrack(
        transferId = "tx_1",
        track = testTrack,
        fileHash = "abc123",
        fileSize = 2048L,
        fileExt = "mp3",
        hasBinary = true,
        audioDownloadUrl = "http://192.168.1.50:45899/audio/abc123"
      )
    )
    assertEquals("http://192.168.1.50:45899/audio/abc123", prepareJson.getString("audioDownloadUrl"))

    val syncPlayJson = org.json.JSONObject(
      com.example.network.RoomProtocol.encodeSyncPlay(
        track = testTrack,
        positionMs = 12500L,
        speed = 1.25f,
        isPlaying = true,
        syncDelayMs = 180L
      )
    )
    val parsedCmd = com.example.network.RoomProtocol.parseSyncCommand(syncPlayJson)
    assertNotNull(parsedCmd)
    assertEquals("SYNC_PLAY", parsedCmd?.action)
    assertEquals("Sunset Rhythm", parsedCmd?.title)
    assertEquals(12500L, parsedCmd?.positionMs)
    assertEquals(1.25f, parsedCmd?.speed ?: 0f, 0.01f)
  }

  @Test
  fun `music playback service starts cleanly with MediaStyle notification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    PlaybackManager.initialize(context)
    val track = com.example.model.Track(
      id = "notif_track_1",
      title = "Asd Acoustic Melody",
      artist = "Asd Studio",
      durationSeconds = 180
    )
    PlaybackManager.playTrack(track, listOf(track), context)

    val controller = org.robolectric.Robolectric.buildService(com.example.service.MusicPlaybackService::class.java)
      .create()
      .startCommand(0, 0)
    val service = controller.get()
    assertNotNull(service)
    controller.destroy()
  }

  @Test
  fun `room protocol computes audio payload hash and synced playback uses transferred file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    PlaybackManager.initialize(context)

    val tempSong = java.io.File(context.cacheDir, "host_shared_song.mp3").apply {
      writeBytes(ByteArray(2048) { (it % 251).toByte() })
    }
    val hostTrack = com.example.model.Track(
      id = "device_101",
      title = "Host Exact Song",
      artist = "Host Artist",
      durationSeconds = 200,
      localFilePath = tempSong.absolutePath
    )

    val payload = com.example.network.RoomProtocol.resolveTrackAudioPayload(context, hostTrack)
    assertNotNull(payload)
    assertEquals(2048L, payload!!.fileSize)
    assertEquals("mp3", payload.fileExt)
    assertEquals(64, payload.fileHash.length)

    val syncJson = org.json.JSONObject(
      com.example.network.RoomProtocol.encodeSyncPlay(
        track = hostTrack,
        positionMs = 0L,
        speed = 1.0f,
        isPlaying = true,
        syncDelayMs = 180L,
        fileHash = payload.fileHash,
        fileSize = payload.fileSize,
        fileExt = payload.fileExt
      )
    )
    val parsed = com.example.network.RoomProtocol.parseSyncCommand(syncJson)
    assertNotNull(parsed)
    assertEquals(payload.fileHash, parsed?.fileHash)
    assertEquals(2048L, parsed?.fileSize)
    assertEquals("mp3", parsed?.fileExt)

    // Verify PlaybackManager reloads when localFilePath updates for same track ID
    val syncedTrack = hostTrack.copy(id = "room_sync_${payload.fileHash}", localFilePath = tempSong.absolutePath)
    PlaybackManager.applySyncedTrackPlayback(syncedTrack, 0L, 1.0f, true, context)
    assertEquals(tempSong.absolutePath, PlaybackManager.playbackState.value.currentTrack?.localFilePath)
    assertEquals("Host Exact Song", PlaybackManager.playbackState.value.currentTrack?.title)

    tempSong.delete()
  }

  @Test
  fun `network room host transfers song A then switches to song B on guest over localhost TCP and HTTP`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val receivedCommands = java.util.concurrent.CopyOnWriteArrayList<com.example.model.RoomSyncCommand>()
    val songAReady = java.util.concurrent.CountDownLatch(1)
    val songBReady = java.util.concurrent.CountDownLatch(1)

    val host = com.example.network.NetworkRoomManager(context = context)
    val guest = com.example.network.NetworkRoomManager(
      context = context,
      onGuestSyncCommand = { cmd ->
        receivedCommands.add(cmd)
        if (cmd.action == "SYNC_PLAY" && cmd.title == "Song Alpha") songAReady.countDown()
        if (cmd.action == "SYNC_PLAY" && cmd.title == "Song Beta") songBReady.countDown()
      }
    )

    val songAFile = java.io.File(context.cacheDir, "net_song_a.mp3").apply {
      writeBytes(ByteArray(3072) { ((it * 3 + 7) % 251).toByte() })
    }
    val songBFile = java.io.File(context.cacheDir, "net_song_b.mp3").apply {
      writeBytes(ByteArray(4096) { ((it * 5 + 13) % 241).toByte() })
    }

    try {
      val code = host.createRoom()
      assertNotNull(code)

      guest.joinRoom(code!!, "127.0.0.1")

      val waitJoinDeadline = System.currentTimeMillis() + 5000L
      while (System.currentTimeMillis() < waitJoinDeadline &&
        (host.roomState.value.members.size < 2 || guest.roomState.value.role != com.example.model.RoomRole.GUEST)
      ) {
        Thread.sleep(50L)
      }
      assertEquals(2, host.roomState.value.members.size)

      host.startRoom()
      val waitStartDeadline = System.currentTimeMillis() + 3000L
      while (System.currentTimeMillis() < waitStartDeadline &&
        guest.roomState.value.phase != com.example.model.RoomPhase.PLAYING
      ) {
        Thread.sleep(50L)
      }
      assertEquals(com.example.model.RoomPhase.PLAYING, guest.roomState.value.phase)

      fun awaitLatchPumpingMainLooper(latch: java.util.concurrent.CountDownLatch, timeoutMs: Long = 5000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        val mainLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        while (System.currentTimeMillis() < deadline && latch.count > 0L) {
          mainLooper.idle()
          Thread.sleep(25L)
        }
        mainLooper.idle()
        return latch.count == 0L
      }

      val trackA = com.example.model.Track(
        id = "device_1",
        title = "Song Alpha",
        artist = "Host",
        durationSeconds = 180,
        localFilePath = songAFile.absolutePath
      )
      val hostPlayedA = java.util.concurrent.CountDownLatch(1)
      host.prepareAndPlayTrack(trackA, 0L, 1.0f) { hostPlayedA.countDown() }
      assertTrue(songAReady.await(8, java.util.concurrent.TimeUnit.SECONDS))
      assertTrue(awaitLatchPumpingMainLooper(hostPlayedA))

      val cmdA = receivedCommands.last { it.title == "Song Alpha" }
      assertNotNull(cmdA.localSyncedFilePath)
      val downloadedA = java.io.File(cmdA.localSyncedFilePath!!)
      assertTrue(downloadedA.exists())
      assertEquals(3072L, downloadedA.length())

      val trackB = com.example.model.Track(
        id = "device_2",
        title = "Song Beta",
        artist = "Host",
        durationSeconds = 210,
        localFilePath = songBFile.absolutePath
      )
      val hostPlayedB = java.util.concurrent.CountDownLatch(1)
      host.prepareAndPlayTrack(trackB, 0L, 1.0f) { hostPlayedB.countDown() }
      assertTrue(songBReady.await(8, java.util.concurrent.TimeUnit.SECONDS))
      assertTrue(awaitLatchPumpingMainLooper(hostPlayedB))

      val cmdB = receivedCommands.last { it.title == "Song Beta" }
      assertNotNull(cmdB.localSyncedFilePath)
      val downloadedB = java.io.File(cmdB.localSyncedFilePath!!)
      assertTrue(downloadedB.exists())
      assertEquals(4096L, downloadedB.length())
      assertTrue(cmdA.fileHash != cmdB.fileHash)
    } finally {
      guest.release()
      host.release()
      songAFile.delete()
      songBFile.delete()
    }
  }
}
