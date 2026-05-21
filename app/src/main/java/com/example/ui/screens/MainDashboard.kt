package com.example.ui.screens

import android.media.audiofx.PresetReverb
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.PlaylistEntity
import com.example.data.db.SongEntity
import com.example.data.sync.GoogleDriveSyncManager.SyncStatus
import com.example.ui.theme.*
import com.example.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel & state observers
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val allPlaylists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val playlistSongs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()

    val currentSong by viewModel.playerManager.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsStateWithLifecycle()
    val progress by viewModel.playerManager.playbackProgress.collectAsStateWithLifecycle()
    val duration by viewModel.playerManager.duration.collectAsStateWithLifecycle()

    val speed by viewModel.playerManager.speedVal.collectAsStateWithLifecycle()
    val pitch by viewModel.playerManager.pitchVal.collectAsStateWithLifecycle()
    val reverbEnabled by viewModel.playerManager.isReverbEnabled.collectAsStateWithLifecycle()
    val reverbPreset by viewModel.playerManager.reverbPreset.collectAsStateWithLifecycle()

    val playingQueue by viewModel.playerManager.playingQueue.collectAsStateWithLifecycle()
    val queueIndex by viewModel.playerManager.queueIndex.collectAsStateWithLifecycle()
    val isShuffle by viewModel.playerManager.isShuffle.collectAsStateWithLifecycle()
    val isRepeat by viewModel.playerManager.isRepeatAll.collectAsStateWithLifecycle()

    val conversionProgress by viewModel.conversionProgress.collectAsStateWithLifecycle()
    val isConverting by viewModel.isConverting.collectAsStateWithLifecycle()
    val conversionLog by viewModel.conversionLog.collectAsStateWithLifecycle()

    val driveSyncState by viewModel.syncManager.syncState.collectAsStateWithLifecycle()
    val lastSyncTime by viewModel.syncManager.lastSyncTime.collectAsStateWithLifecycle()
    val driveAuthToken by viewModel.driveAuthToken.collectAsStateWithLifecycle()

    // Navigation and UI state
    var selectedTab by remember { mutableStateOf(0) } // 0: Tracks, 1: Playlists, 2: Converter, 3: Sync
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    var showAddToPlaylistSheet by remember { mutableStateOf<SongEntity?>(null) }

    // Activity result launcher for audio files import
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importLocalAudio(it) }
    }

    // Activity result launcher for video conversion
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileDisplayName = "Extracted_${System.currentTimeMillis()}"
            viewModel.convertVideoToAudio(it, fileDisplayName)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                // Collapsed bottom music player bar
                if (currentSong != null && !isPlayerExpanded) {
                    CollapsedPlayerPanel(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        progress = progress,
                        duration = duration,
                        onPlayPauseClick = { viewModel.playOrPause() },
                        onNextClick = { viewModel.skipToNext() },
                        onPanelClick = { isPlayerExpanded = true }
                    )
                }

                // Standard stylish M3 Navigation Bar
                NavigationBar(
                    containerColor = MatteSlate.copy(alpha = 0.85f),
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0 && selectedPlaylist == null,
                        onClick = {
                            selectedTab = 0
                            viewModel.clearPlaylistSelection()
                        },
                        icon = { Icon(Icons.Filled.MusicNote, contentDescription = "Tracks") },
                        label = { Text("Tracks", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricCyan,
                            selectedTextColor = ElectricCyan,
                            indicatorColor = GlassGrey
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1 || selectedPlaylist != null,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.QueueMusic, contentDescription = "Playlists") },
                        label = { Text("Playlists", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CosmicPurple,
                            selectedTextColor = CosmicPurple,
                            indicatorColor = GlassGrey
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = "Converter") },
                        label = { Text("MP3 Converter", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonPink,
                            selectedTextColor = NeonPink,
                            indicatorColor = GlassGrey
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Filled.CloudSync, contentDescription = "Cloud Sync") },
                        label = { Text("Drive", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldGlow,
                            selectedTextColor = EmeraldGlow,
                            indicatorColor = GlassGrey
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DeepObsidian, Color(0xFF10131B))
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header of screen
                HeaderSection(
                    title = when {
                        selectedPlaylist != null -> "Playlist: ${selectedPlaylist!!.name}"
                        selectedTab == 0 -> "All Soundtracks"
                        selectedTab == 1 -> "My Playlists"
                        selectedTab == 2 -> "Video Audio Extraction"
                        else -> "Google Drive Sync"
                    },
                    subtitle = when {
                        selectedPlaylist != null -> "Sonic Player Playlist Manager"
                        selectedTab == 0 -> "High frequency offline music deck"
                        selectedTab == 1 -> "Offline curated queues"
                        selectedTab == 2 -> "Mux direct AAC content offline"
                        else -> "Sync lists & customized local files"
                    },
                    onBackClick = if (selectedPlaylist != null) {
                        { viewModel.clearPlaylistSelection() }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Active Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        selectedPlaylist != null -> {
                            PlaylistSongsScreen(
                                playlistName = selectedPlaylist!!.name,
                                songs = playlistSongs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onSongClick = { song ->
                                    viewModel.playSong(song, playlistSongs)
                                },
                                onRemoveFromPlaylist = { song ->
                                    viewModel.removeSongFromPlaylist(selectedPlaylist!!.id, song.id)
                                },
                                onAddToQueue = { song ->
                                    viewModel.playerManager.addToQueueEnd(song)
                                },
                                listToUse = playlistSongs
                            )
                        }
                        selectedTab == 0 -> {
                            SongsTab(
                                songs = allSongs,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onSongClick = { song ->
                                    viewModel.playSong(song, allSongs)
                                },
                                onImportClick = { audioPickerLauncher.launch("audio/*") },
                                onAddToPlaylist = { song -> showAddToPlaylistSheet = song },
                                onDeleteClick = { song -> viewModel.deleteSong(song) },
                                onAddToQueueNext = { song -> viewModel.playerManager.addToQueueNext(song) },
                                onAddToQueueEnd = { song -> viewModel.playerManager.addToQueueEnd(song) }
                            )
                        }
                        selectedTab == 1 -> {
                            PlaylistsTab(
                                playlists = allPlaylists,
                                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                onPlaylistSelected = { viewModel.loadPlaylistSongs(it) },
                                onDeletePlaylist = { viewModel.deletePlaylist(it) }
                            )
                        }
                        selectedTab == 2 -> {
                            ConverterTab(
                                isConverting = isConverting,
                                progress = conversionProgress,
                                log = conversionLog,
                                onPickVideoClick = { videoPickerLauncher.launch("video/*") }
                            )
                        }
                        selectedTab == 3 -> {
                            SyncTab(
                                syncState = driveSyncState,
                                lastSyncTime = lastSyncTime,
                                driveToken = driveAuthToken,
                                onTokenChange = { viewModel.updateDriveToken(it) },
                                onSyncClick = { viewModel.syncDataWithDrive() },
                                onDisconnectClick = { viewModel.disconnectDrive() }
                            )
                        }
                    }
                }
            }

            // Expanded full screen modern glass dynamic player
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                if (currentSong != null) {
                    ExpandedPlayerPanel(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        progress = progress,
                        duration = duration,
                        speed = speed,
                        pitch = pitch,
                        reverbEnabled = reverbEnabled,
                        reverbPreset = reverbPreset,
                        playingQueue = playingQueue,
                        queueIndex = queueIndex,
                        isShuffle = isShuffle,
                        isRepeat = isRepeat,
                        onCloseClick = { isPlayerExpanded = false },
                        onPlayPauseClick = { viewModel.playOrPause() },
                        onNextClick = { viewModel.skipToNext() },
                        onPrevClick = { viewModel.skipToPrevious() },
                        onSeek = { viewModel.seekTo(it) },
                        onSpeedChange = { viewModel.setPlaybackEffects(it, pitch) },
                        onPitchChange = { viewModel.setPlaybackEffects(speed, it) },
                        onReverbToggle = { viewModel.toggleReverb(it) },
                        onReverbPresetChange = { viewModel.setReverbPreset(it) },
                        onQueueReorder = { from, to -> viewModel.playerManager.reorderQueue(from, to) },
                        onQueueRemove = { idx -> viewModel.playerManager.removeFromQueue(idx) },
                        onToggleShuffle = { viewModel.playerManager.toggleShuffle() },
                        onToggleRepeat = { viewModel.playerManager.toggleRepeat() }
                    )
                }
            }
        }
    }

    // Dialog: Create Playlist
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmicPurple,
                        cursorColor = CosmicPurple
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            viewModel.createPlaylist(playlistNameInput)
                            playlistNameInput = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicPurple)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = AudioMutedText)
                }
            },
            containerColor = MatteSlate
        )
    }

    // Dialog / Sheet: Add Song to Playlist
    if (showAddToPlaylistSheet != null) {
        val songToAdd = showAddToPlaylistSheet!!
        AlertDialog(
            onDismissRequest = { showAddToPlaylistSheet = null },
            title = { Text("Add directly to Playlist", color = Color.White, fontSize = 18.sp) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    if (allPlaylists.isEmpty()) {
                        item {
                            Text(
                                "No Playlists configured. Create one in the Playlists tab!",
                                color = AudioMutedText,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        itemsIndexed(allPlaylists) { _, pl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        viewModel.addSongToPlaylist(pl.id, songToAdd.id)
                                        showAddToPlaylistSheet = null
                                    },
                                colors = CardDefaults.cardColors(containerColor = GlassGrey)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = CosmicPurple)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(pl.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistSheet = null }) {
                    Text("Close", color = Color.White)
                }
            },
            containerColor = MatteSlate
        )
    }
}

// Sub-component: Header Section
@Composable
fun HeaderSection(
    title: String,
    subtitle: String,
    onBackClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(CircleShape)
                    .background(GlassGrey)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = Color.White)
            }
        }

        Column {
            Text(
                text = title,
                fontSize = 24.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AudioMutedText,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Sub-component: All Tracks Tab
@Composable
fun SongsTab(
    songs: List<SongEntity>,
    currentSong: SongEntity?,
    isPlaying: Boolean,
    onSongClick: (SongEntity) -> Unit,
    onImportClick: () -> Unit,
    onAddToPlaylist: (SongEntity) -> Unit,
    onDeleteClick: (SongEntity) -> Unit,
    onAddToQueueNext: (SongEntity) -> Unit,
    onAddToQueueEnd: (SongEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Quick import panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlassGrey)
                .clickable { onImportClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = ElectricCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Offline Audio File", color = ElectricCyan, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (songs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = null,
                            tint = AudioMutedText,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No soundtracks found",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Import songs or convert standard videos details",
                            color = AudioMutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(songs) { _, song ->
                    SongListItem(
                        song = song,
                        isActive = song.id == currentSong?.id,
                        isPlaying = isPlaying,
                        onClick = { onSongClick(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onDelete = { onDeleteClick(song) },
                        onAddToQueueNext = { onAddToQueueNext(song) },
                        onAddToQueueEnd = { onAddToQueueEnd(song) }
                    )
                }
            }
        }
    }
}

// Sub-component: Playlists Tab View
@Composable
fun PlaylistsTab(
    playlists: List<PlaylistEntity>,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistSelected: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(CosmicPurple, MatteSlate)
                    )
                )
                .clickable { onCreatePlaylistClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Custom Playlist", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (playlists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = AudioMutedText,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Curate customized collections", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Group your favorite high slowed configurations together",
                            color = AudioMutedText,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                itemsIndexed(playlists) { _, pl ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistSelected(pl) },
                        colors = CardDefaults.cardColors(containerColor = MatteSlate.copy(alpha = 0.6f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = CosmicPurple,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pl.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "Custom play compilation",
                                    color = AudioMutedText,
                                    fontSize = 12.sp
                                )
                            }
                            IconButton(
                                onClick = { onDeletePlaylist(pl.id) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sub-component: Playlist Details Page View
@Composable
fun PlaylistSongsScreen(
    playlistName: String,
    songs: List<SongEntity>,
    currentSong: SongEntity?,
    isPlaying: Boolean,
    onSongClick: (SongEntity) -> Unit,
    onRemoveFromPlaylist: (SongEntity) -> Unit,
    onAddToQueue: (SongEntity) -> Unit,
    listToUse: List<SongEntity>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (songs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SettingsVoice,
                        contentDescription = null,
                        tint = AudioMutedText,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This playlist is currently empty", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Choose songs in All Songs tab and add them here to play!", color = AudioMutedText, fontSize = 12.sp)
                }
            }
        } else {
            itemsIndexed(songs) { _, song ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (song.id == currentSong?.id) GlassGrey else MatteSlate
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onSongClick(song) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Wave icon for currently playing item
                        if (song.id == currentSong?.id) {
                            AnimatedEqualizerBars(isPlaying = isPlaying)
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = if (song.id == currentSong?.id) ElectricCyan else Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = AudioMutedText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onAddToQueue(song) }
                        ) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Queue", tint = Color.White)
                        }

                        IconButton(
                            onClick = { onRemoveFromPlaylist(song) }
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove From Playlist", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

// Sub-component: Video to Track Converter Panel
@Composable
fun ConverterTab(
    isConverting: Boolean,
    progress: Int,
    log: String,
    onPickVideoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.MovieFilter,
                contentDescription = null,
                tint = NeonPink,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Offline MP4-to-MP3 Studio",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Select a video file to extract high definition audio natively",
                color = AudioMutedText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!isConverting) {
                Button(
                    onClick = onPickVideoClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Local Video File", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.size(88.dp),
                        color = NeonPink,
                        strokeWidth = 6.dp,
                    )
                    Text("$progress%", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Extraction terminal output
            if (log.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConverting) Color.Green else Color.White)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isConverting) "EXTRACTION ACTIVE" else "STUDIO LOG",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            log,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// Sub-component: Sync Tab View
@Composable
fun SyncTab(
    syncState: SyncStatus,
    lastSyncTime: Long,
    driveToken: String,
    onTokenChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Icon(
                    Icons.Default.VpnLock,
                    contentDescription = null,
                    tint = EmeraldGlow,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Google Drive Backup Portal",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Preserve all custom configurations, playlists, metadata indexing",
                    color = AudioMutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Connection state Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GlassGrey,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "SYNC STATE PROPERTIES",
                            color = EmeraldGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (driveToken.isNotBlank()) ActiveGreen else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (driveToken.isNotBlank()) "Linked to Google Drive [REST ACTIVE]" else "Simulated Cloud Syncer Mode [OFFLINE READY]",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Last Sync: ${if (lastSyncTime > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(lastSyncTime)) else "Never synced"}",
                            color = AudioMutedText,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Optional Authentication input for live REST connections
            item {
                OutlinedTextField(
                    value = driveToken,
                    onValueChange = onTokenChange,
                    label = { Text("Developer OAuth Bearer Token (Optional)") },
                    placeholder = { Text("Enter Google API Bearer string") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldGlow,
                        cursorColor = EmeraldGlow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = onSyncClick,
                    enabled = syncState !is SyncStatus.Syncing,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (syncState is SyncStatus.Syncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Initiate Google Backup Sync", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (driveToken.isNotBlank()) {
                item {
                    TextButton(onClick = onDisconnectClick) {
                        Text("Disconnect OAuth Token", color = NeonPink)
                    }
                }
            }

            // Sync Status feedback log
            item {
                AnimatedContent(targetState = syncState) { status ->
                    when (status) {
                        is SyncStatus.Success -> {
                            Text(
                                "Database fully synced to backup.json successfully!",
                                color = ActiveGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        is SyncStatus.Error -> {
                            Text(
                                "Sync connection interrupted: ${status.message}. Config secure.",
                                color = NeonPink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

// Sub-component: Bottom compact slider player
@Composable
fun CollapsedPlayerPanel(
    song: SongEntity,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPanelClick: () -> Unit
) {
    Surface(
        color = MatteSlate.copy(alpha = 0.9f),
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clickable { onPanelClick() }
    ) {
        Column {
            // Track progress bar line immediately on top
            val progressFraction = if (duration > 0) progress.toFloat() / duration else 0f
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ElectricCyan,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Disk artwork simulator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(ElectricCyan, CosmicPurple, ElectricCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = AudioMutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = ElectricCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onNextClick) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// Sub-component: Expanded Real-time audio console
@Composable
fun ExpandedPlayerPanel(
    song: SongEntity,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    speed: Float,
    pitch: Float,
    reverbEnabled: Boolean,
    reverbPreset: Int,
    playingQueue: List<SongEntity>,
    queueIndex: Int,
    isShuffle: Boolean,
    isRepeat: Boolean,
    onCloseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onReverbToggle: (Boolean) -> Unit,
    onReverbPresetChange: (Int) -> Unit,
    onQueueReorder: (Int, Int) -> Unit,
    onQueueRemove: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    var activePlayerTab by remember { mutableStateOf(0) } // 0: Player deck, 1: Live Skip Queue List

    // Infinite transition for spinning CD disk graphic
    val infiniteTransition = rememberInfiniteTransition()
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CD rotate"
    )

    Surface(
        color = DeepObsidian,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Drop down toggle and Queue selector tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCloseClick) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                // Player / Queue toggle tabs in the dashboard header
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(GlassGrey)
                        .padding(4.dp)
                ) {
                    val activeBg = Brush.horizontalGradient(listOf(ElectricCyan, CosmicPurple))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (activePlayerTab == 0) ElectricCyan else Color.Transparent)
                            .clickable { activePlayerTab = 0 }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Effects Console",
                            color = if (activePlayerTab == 0) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (activePlayerTab == 1) CosmicPurple else Color.Transparent)
                            .clickable { activePlayerTab = 1 }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Skip Queue (${playingQueue.size})",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(modifier = Modifier.size(32.dp)) // Spacer to align
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (activePlayerTab == 0) {
                // Tab 1: Full console: slowed & reverb adjustments + dynamic visuals
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Spinning CD Disk Graphic (Rotating disc)
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .graphicsLayer {
                                rotationZ = if (isPlaying) rotationAngle else 0f
                            }
                            .shadow(24.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(DeepObsidian, ElectricCyan, CosmicPurple, DeepObsidian)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Center label groove represent vinyl
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = Color.Black.copy(alpha = 0.5f), radius = size.minDimension / 2.3f)
                            drawCircle(color = Color.Black, radius = size.minDimension / 6f)
                        }
                        Icon(Icons.Default.Album, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(44.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        color = AudioMutedText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dynamic Audio Frequency Visualizer simulation (real-time equalizer wave)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barsCount = 28
                        val modifierFactor = if (isPlaying) 1f else 0.08f
                        for (i in 0 until barsCount) {
                            VisualizerWaveBar(index = i, modifierFactor = modifierFactor)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback progress slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = progress.toFloat(),
                            onValueChange = { onSeek(it.toLong()) },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = GlassGrey
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(progress), color = AudioMutedText, fontSize = 11.sp)
                            Text(formatTime(duration), color = AudioMutedText, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ADVANCED FX DECK (Slowed and reverb controls)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = GlassGrey)
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Part 1: Slowed controller (0.5x - 1.5x speed)
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("CUSTOM SLOWED [Speed]", color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("${String.format("%.2f", speed)}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Slider(
                                        value = speed,
                                        onValueChange = onSpeedChange,
                                        valueRange = 0.5f..1.5f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = ElectricCyan,
                                            activeTrackColor = ElectricCyan,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )
                                }
                            }

                            // Part 2: Pitch shift controller (0.5x - 1.5x speed)
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("PITCH GLINT", color = CosmicPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("${String.format("%.2f", pitch)}x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Slider(
                                        value = pitch,
                                        onValueChange = onPitchChange,
                                        valueRange = 0.5f..1.5f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = CosmicPurple,
                                            activeTrackColor = CosmicPurple,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )
                                }
                            }

                            // Part 3: Reverb setup
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("ENVIRONMENT REVERB", color = EmeraldGlow, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("Realistic echo spacing effect", color = AudioMutedText, fontSize = 10.sp)
                                    }
                                    Switch(
                                        checked = reverbEnabled,
                                        onCheckedChange = onReverbToggle,
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = EmeraldGlow,
                                            checkedTrackColor = EmeraldGlow.copy(alpha = 0.4f)
                                        )
                                    )
                                }
                            }

                            // Part 4: Reverb sizes selector chips
                            if (reverbEnabled) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val presets = listOf(
                                            Pair("Plate", PresetReverb.PRESET_PLATE),
                                            Pair("S-Room", PresetReverb.PRESET_SMALLROOM),
                                            Pair("M-Hall", PresetReverb.PRESET_MEDIUMHALL),
                                            Pair("L-Hall", PresetReverb.PRESET_LARGEHALL)
                                        )
                                        presets.forEach { pair ->
                                            val isSelected = reverbPreset == pair.second.toInt()
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) EmeraldGlow else Color.Black)
                                                    .clickable { onReverbPresetChange(pair.second.toInt()) }
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    pair.first,
                                                    color = if (isSelected) Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 2: Interactive Skip Queue list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Text(
                        "Playlist Active Skip Queue",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Drag or handle buttons to skip list tracks immediately",
                        color = AudioMutedText,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(playingQueue) { index, qSong ->
                            val isPlayingThis = index == queueIndex
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPlayingThis) GlassGrey else MatteSlate
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = if (isPlayingThis) CosmicPurple else AudioMutedText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            qSong.title,
                                            color = if (isPlayingThis) ElectricCyan else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            qSong.artist,
                                            color = AudioMutedText,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Up and Down button for custom queue ordering triggers
                                    Row {
                                        IconButton(
                                            onClick = { if (index > 0) onQueueReorder(index, index - 1) },
                                            enabled = index > 0
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = Color.White.copy(alpha = 0.6f))
                                        }
                                        IconButton(
                                            onClick = { if (index < playingQueue.size - 1) onQueueReorder(index, index + 1) },
                                            enabled = index < playingQueue.size - 1
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = Color.White.copy(alpha = 0.6f))
                                        }
                                    }

                                    IconButton(onClick = { onQueueRemove(index) }) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Global Music Control Deck (Bottom of player screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) ElectricCyan else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onPrevClick) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Glowing Play-Pause container box
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricCyan, CosmicPurple)
                            )
                        )
                        .clickable { onPlayPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onNextClick) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) CosmicPurple else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Helper: Formatter time strings
private fun formatTime(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// Sub-component: Equalizer Wave Animation
@Composable
fun AnimatedEqualizerBars(isPlaying: Boolean) {
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = listOf(0.4f, 0.9f, 0.6f, 0.1f)
        bars.forEachIndexed { i, multiplier ->
            EqualizerBarItem(index = i, multiplier = multiplier, isPlaying = isPlaying)
        }
    }
}

@Composable
fun EqualizerBarItem(index: Int, multiplier: Float, isPlaying: Boolean) {
    val heightAnim by rememberInfiniteTransition(label = "equalizer bar $index").animateFloat(
        initialValue = 2f,
        targetValue = 18f * multiplier,
        animationSpec = infiniteRepeatable(
            animation = tween(280 + (index * 120), easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "equalizer bar float $index"
    )
    Box(
        modifier = Modifier
            .width(3.dp)
            .height(if (isPlaying) heightAnim.dp else 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(ElectricCyan)
    )
}

@Composable
fun RowScope.VisualizerWaveBar(index: Int, modifierFactor: Float) {
    val animHeight by rememberInfiniteTransition(label = "wave annotation $index").animateFloat(
        initialValue = 8f,
        targetValue = 24f + (kotlin.math.sin(index.toFloat() * 0.7f) * 16),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 400 + (index * 25), 
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave animation float $index"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 1.dp)
            .fillMaxHeight(fraction = (animHeight * modifierFactor / 52f).coerceIn(0.05f, 1f))
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ElectricCyan, CosmicPurple)
                )
            )
    )
}

// Sub-component: Playback Item lists
@Composable
fun SongListItem(
    song: SongEntity,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onAddToQueueNext: () -> Unit,
    onAddToQueueEnd: () -> Unit
) {
    var expandedDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) GlassGrey else MatteSlate.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Equalizer bar indicator when song is active
            if (isActive) {
                AnimatedEqualizerBars(isPlaying = isPlaying)
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = if (isActive) ElectricCyan else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    color = AudioMutedText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Dropdown menu trigger for quick lists and delete entries
            Box {
                IconButton(onClick = { expandedDropdown = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
                }

                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(MatteSlate)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = Color.White) },
                        onClick = {
                            expandedDropdown = false
                            onAddToPlaylist()
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = CosmicPurple) }
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next in Queue", color = Color.White) },
                        onClick = {
                            expandedDropdown = false
                            onAddToQueueNext()
                        },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ElectricCyan) }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to End of Queue", color = Color.White) },
                        onClick = {
                            expandedDropdown = false
                            onAddToQueueEnd()
                        },
                        leadingIcon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null, tint = EmeraldGlow) }
                    )
                    if (!song.isPreloaded) {
                        DropdownMenuItem(
                            text = { Text("Delete Track From Device", color = Color.Red) },
                            onClick = {
                                expandedDropdown = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }
        }
    }
}
