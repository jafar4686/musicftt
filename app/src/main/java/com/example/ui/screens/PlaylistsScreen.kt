package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DownloadItem
import com.example.model.Playlist
import com.example.model.Track
import com.example.ui.components.TrackListItem
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HologramPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SoftViolet
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlaylistsScreen(
    allPlaylists: List<Playlist>,
    userPlaylists: List<Playlist>,
    selectedPlaylist: Playlist?,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteTrackIds: Set<String>,
    downloads: List<DownloadItem>,
    onSelectPlaylist: (Playlist?) -> Unit,
    onOpenCreateDialog: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayPlaylist: (Playlist, Int) -> Unit,
    onToggleLikePlaylist: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadAudio: (Track) -> Unit,
    onDownloadVideo: (Track) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onSharePlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedPlaylist != null) {
        // Detailed Playlist View
        PlaylistDetailView(
            playlist = selectedPlaylist,
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            favoriteTrackIds = favoriteTrackIds,
            downloads = downloads,
            onBack = { onSelectPlaylist(null) },
            onPlayTrack = onPlayTrack,
            onPlayAll = { onPlayPlaylist(selectedPlaylist, 0) },
            onShuffleAll = { onPlayPlaylist(selectedPlaylist.copy(tracks = selectedPlaylist.tracks.shuffled()), 0) },
            onToggleLike = { onToggleLikePlaylist(selectedPlaylist.id) },
            onToggleFavorite = onToggleFavorite,
            onDownloadAudio = onDownloadAudio,
            onDownloadVideo = onDownloadVideo,
            onRemoveDownload = onRemoveDownload,
            onShare = { onSharePlaylist(selectedPlaylist) },
            modifier = modifier
        )
    } else {
        // Playlists Main Hub View
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val tabs = listOf("All Playlists", "Created By You", "Curated")

        val displayedPlaylists = when (selectedTabIndex) {
            1 -> userPlaylists
            2 -> allPlaylists.filter { it.isCurated }
            else -> allPlaylists
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("playlists_screen"),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "COLLECTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Playlists & Mixes",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons: Create & Import
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenCreateDialog,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_playlist_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "New Playlist", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenImportDialog,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SoftViolet),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftViolet),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_code_button")
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Enter Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Tab bar
            item {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = ElectricCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = ElectricCyan
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) ElectricCyan else TextSecondary
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Playlist cards
            items(displayedPlaylists) { playlist ->
                PlaylistItemCard(
                    playlist = playlist,
                    onClick = { onSelectPlaylist(playlist) },
                    onShare = { onSharePlaylist(playlist) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun PlaylistItemCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141520),
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFF242638), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("playlist_row_${playlist.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gradient Icon Box
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(playlist.gradientStart), Color(playlist.gradientEnd))
                        )
                    )
            ) {
                Text(text = playlist.coverEmoji, fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.title,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (playlist.isCurated) {
                        Box(
                            modifier = Modifier
                                .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(text = "CURATED", fontSize = 8.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = playlist.description,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Code: ${playlist.shareCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SoftViolet
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "• ${playlist.tracks.size} tracks",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Playlist",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteTrackIds: Set<String>,
    downloads: List<DownloadItem>,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDownloadAudio: (Track) -> Unit,
    onDownloadVideo: (Track) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlist_detail_view"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Hero Header with Gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(playlist.gradientStart).copy(alpha = 0.5f),
                                ObsidianBlack
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Row {
                            IconButton(onClick = onToggleLike, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = if (playlist.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Like Playlist",
                                    tint = if (playlist.isLiked) HologramPink else Color.White
                                )
                            }
                            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Playlist",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(playlist.gradientStart), Color(playlist.gradientEnd))
                                    )
                                )
                        ) {
                            Text(text = playlist.coverEmoji, fontSize = 42.sp)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Box(
                                modifier = Modifier
                                    .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SHARE CODE: ${playlist.shareCode}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playlist.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "By ${playlist.createdBy} • ${playlist.formattedDuration}",
                                fontSize = 12.sp,
                                color = SoftViolet
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = playlist.description,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Play & Shuffle buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onPlayAll,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Play All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onShuffleAll,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SoftViolet),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftViolet),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Shuffle", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Track List Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tracklist (${playlist.tracks.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${playlist.likesCount} ❤️ likes",
                    fontSize = 12.sp,
                    color = HologramPink
                )
            }
        }

        // Track items
        items(playlist.tracks) { track ->
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
