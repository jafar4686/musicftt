package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicRepository
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
    assertEquals("Aura Music", appName)
  }

  @Test
  fun `repository loads curated playlists and supports creation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = MusicRepository(context)

    val initialPlaylists = repository.allPlaylists.value
    assertTrue("Should have initial curated playlists", initialPlaylists.isNotEmpty())

    val created = repository.createPlaylist(
      title = "Night Drive",
      description = "Cyberpunk synth",
      emoji = "⚡",
      initialTrackIds = listOf("t1")
    )
    assertNotNull(created)
    assertEquals("Night Drive", created.title)
    assertTrue("Share code should be generated", created.shareCode.startsWith("VIBE-"))
  }
}
