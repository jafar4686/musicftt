package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadItem
import com.example.model.FriendActivity
import com.example.model.Playlist
import com.example.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HologramPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DiscoverScreen(
    playlists: List<Playlist>,
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteTrackIds: Set<String>,
    downloads: List<DownloadItem>,
    friendsActivity: List<FriendActivity>,
    selectedGenre: String?,
    onSelectGenre: (String?) -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadAudio: (Track) -> Unit,
    onDownloadVideo: (Track) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onSharePlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val genres = listOf("All", "Cyberpunk", "Chillhop", "Ambient", "Synthwave", "Lo-Fi")

    val filteredTracks = if (selectedGenre == null || selectedGenre == "All") {
        tracks
    } else {
        tracks.filter { it.genre.equals(selectedGenre, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("discover_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Hero Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DISCOVER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Curated Vibes",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    // Friends listening live pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF181926))
                            .border(0.5.dp, BorderSubtleGlow, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${friendsActivity.count { it.isLive }} Friends Online",
                            fontSize = 11.sp,
                            color = SoftViolet
                        )
                    }
                }
            }
        }

        // Genre Filter Chips Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genres.forEach { genre ->
                    val isSelected = (selectedGenre == null && genre == "All") || (selectedGenre == genre)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectGenre(if (genre == "All") null else genre) },
                        label = {
                            Text(
                                text = genre,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan,
                            containerColor = Color(0xFF13141F),
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) ElectricCyan else Color(0xFF222434)
                        )
                    )
                }
            }
        }

        // Featured Curated Playlist Banner
        item {
            val featured = playlists.firstOrNull()
            if (featured != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(featured.gradientStart),
                                    Color(featured.gradientEnd)
                                )
                            )
                        )
                        .clickable { onSelectPlaylist(featured) }
                        .testTag("featured_playlist_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        ObsidianBlack.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(ObsidianBlack.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "FEATURED PLAYLIST",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricCyan,
                                        letterSpacing = 1.sp
                                    )
                                }

                                IconButton(
                                    onClick = { onSharePlaylist(featured) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            Text(
                                text = featured.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = featured.description,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Code: ${featured.shareCode} • ${featured.formattedDuration}",
                                    fontSize = 11.sp,
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = ObsidianBlack,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Curated Collections Row
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    text = "Curated Mixes",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            onClick = { onSelectPlaylist(playlist) },
                            onShare = { onSharePlaylist(playlist) }
                        )
                    }
                }
            }
        }

        // Trending Tracks Section
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Tracks",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${filteredTracks.size} available",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // Tracks items
        items(filteredTracks) { track ->
            val isCurrent = currentTrack?.id == track.id
            val isFav = favoriteTrackIds.contains(track.id)
            val downloadItem = downloads.find { it.trackId == track.id }

            TrackListItem(
                track = track,
                isCurrentTrack = isCurrent,
                isPlaying = isPlaying,
                isFavorite = isFav,
                downloadItem = downloadItem,
                onClick = { onPlayTrack(track) },
                onToggleFavorite = { onToggleFavorite(track.id) },
                onDownloadAudio = { onDownloadAudio(track) },
                onDownloadVideo = { onDownloadVideo(track) },
                onRemoveDownload = { onRemoveDownload(track.id) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141520),
        modifier = modifier
            .width(170.dp)
            .border(0.5.dp, Color(0xFF26283C), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("playlist_card_${playlist.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(playlist.gradientStart), Color(playlist.gradientEnd))
                        )
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = playlist.coverEmoji,
                    fontSize = 32.sp,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.TopEnd)
                        .background(ObsidianBlack.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(ObsidianBlack.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = playlist.shareCode,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = playlist.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = playlist.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${playlist.tracks.size} tracks",
                    fontSize = 10.sp,
                    color = SoftViolet,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private val BorderSubtleGlow = Color(0x3300E5FF)
