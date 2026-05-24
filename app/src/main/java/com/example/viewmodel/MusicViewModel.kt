package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongCrossRef
import com.example.data.db.SongEntity
import com.example.data.player.AudioPlayerManager
import com.example.data.repository.MusicRepository
import com.example.data.sync.GoogleDriveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = MusicRepository(application, db)
    val playerManager = AudioPlayerManager.getInstance(application)
    val syncManager = GoogleDriveSyncManager.getInstance(application)

    private val TAG = "MusicViewModel"

    // UI State flows
    val allSongs: StateFlow<List<SongEntity>> = repository.allSongs.stateInViewModel(emptyList())
    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists.stateInViewModel(emptyList())

    // Startup & manual scan states
    private val _isLoadingData = MutableStateFlow(true)
    val isLoadingData: StateFlow<Boolean> = _isLoadingData.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedCount = MutableStateFlow<Int?>(null)
    val scannedCount: StateFlow<Int?> = _scannedCount.asStateFlow()

    private val _newSongsAddedCount = MutableStateFlow<Int?>(null)
    val newSongsAddedCount: StateFlow<Int?> = _newSongsAddedCount.asStateFlow()

    private val _excludedFolders = MutableStateFlow<List<String>>(emptyList())
    val excludedFolders: StateFlow<List<String>> = _excludedFolders.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("dynamic_player_prefs", android.content.Context.MODE_PRIVATE)

    // Currently selected item in the UI
    private val _selectedPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val selectedPlaylist: StateFlow<PlaylistEntity?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val playlistSongs: StateFlow<List<SongEntity>> = _playlistSongs.asStateFlow()

    // Video to MP3 conversion state
    private val _conversionProgress = MutableStateFlow(0)
    val conversionProgress: StateFlow<Int> = _conversionProgress.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _conversionLog = MutableStateFlow("")
    val conversionLog: StateFlow<String> = _conversionLog.asStateFlow()

    // Sync input parameters
    private val _driveAuthToken = MutableStateFlow("")
    val driveAuthToken: StateFlow<String> = _driveAuthToken.asStateFlow()

    // Shared playlist cross references to upload configurations properly
    private var allCrossReferences: List<PlaylistSongCrossRef> = emptyList()

    init {
        loadExcludedFolders()
        // Preload sample tracks and load relations in background
        viewModelScope.launch {
            _isLoadingData.value = true
            try {
                repository.preloadSampleSongs()
                observeDatabaseRelations()
            } catch (e: Exception) {
                Log.e(TAG, "Failed preloading database songs on startup", e)
            } finally {
                _isLoadingData.value = false
            }

            // Perform auto scan silently in the background
            try {
                val countBefore = db.musicDao().getSongsCount()
                val folders = _excludedFolders.value
                repository.scanDeviceForSongs(folders)
                val countAfter = db.musicDao().getSongsCount()
                val newlyAdded = (countAfter - countBefore).coerceAtLeast(0)
                if (newlyAdded > 0) {
                    _newSongsAddedCount.value = newlyAdded
                }
            } catch (e: Exception) {
                Log.e(TAG, "Startup background scan failed", e)
            }
        }
    }

    private fun observeDatabaseRelations() {
        viewModelScope.launch(Dispatchers.IO) {
            db.musicDao().getAllPlaylists().collect {
                // Keep preloaded arrays active for sync schema serialization
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            db.musicDao().getAllPlaylistSongCrossRefs().collect { list ->
                allCrossReferences = list
            }
        }
    }

    // Player Actions
    fun playSong(song: SongEntity, list: List<SongEntity>) {
        val index = list.indexOf(song).coerceAtLeast(0)
        playerManager.setQueue(list, index)
    }

    fun playOrPause() {
        if (playerManager.isPlaying.value) {
            playerManager.pause()
        } else {
            playerManager.play()
        }
    }

    fun skipToNext() {
        playerManager.playNext()
    }

    fun skipToPrevious() {
        playerManager.playPrevious()
    }

    fun seekTo(progressMs: Long) {
        playerManager.seekTo(progressMs)
    }

    fun setPlaybackEffects(speed: Float, pitch: Float) {
        playerManager.setPlaybackParams(speed, pitch)
    }

    fun toggleReverb(enabled: Boolean) {
        playerManager.setReverbEnabled(enabled)
    }

    fun setReverbPreset(preset: Int) {
        playerManager.setReverbPreset(preset)
    }

    // Playlist Actions
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
                _playlistSongs.value = emptyList()
            }
        }
    }

    fun addSongToPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
            // Refresh playlist songs view if active
            _selectedPlaylist.value?.let { active ->
                if (active.id == playlistId) {
                    loadPlaylistSongs(active)
                }
            }
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIds: List<String>) {
        viewModelScope.launch {
            songIds.forEach { songId ->
                repository.addSongToPlaylist(playlistId, songId)
            }
            // Refresh playlist songs view if active
            _selectedPlaylist.value?.let { active ->
                if (active.id == playlistId) {
                    loadPlaylistSongs(active)
                }
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
            // Refresh active lists
            _selectedPlaylist.value?.let { active ->
                if (active.id == playlistId) {
                    loadPlaylistSongs(active)
                }
            }
        }
    }

    fun loadPlaylistSongs(playlist: PlaylistEntity) {
        _selectedPlaylist.value = playlist
        viewModelScope.launch {
            repository.getSongsInPlaylist(playlist.id).collect { songs ->
                _playlistSongs.value = songs
            }
        }
    }

    fun clearPlaylistSelection() {
        _selectedPlaylist.value = null
        _playlistSongs.value = emptyList()
    }

    // Audio Import File
    fun importLocalAudio(uri: Uri) {
        viewModelScope.launch {
            val song = repository.importAudioFile(uri)
            if (song != null) {
                Log.d(TAG, "Successfully imported song: ${song.title}")
            }
        }
    }

    // Delete single song
    fun deleteSong(song: SongEntity) {
        viewModelScope.launch {
            repository.deleteSong(song)
        }
    }

    // Video to MP3 Conversion Actions
    fun convertVideoToAudio(uri: Uri, outputName: String) {
        viewModelScope.launch {
            _isConverting.value = true
            _conversionProgress.value = 0
            _conversionLog.value = "Scanning local video tracks..."
            
            repository.convertVideoToAudio(
                videoUri = uri,
                name = outputName,
                onProgress = { progress ->
                    _conversionProgress.value = progress
                    _conversionLog.value = "Extracting raw AAC audio channels: $progress%"
                },
                onSuccess = { song ->
                    _isConverting.value = false
                    _conversionProgress.value = 100
                    _conversionLog.value = "Success! Saved '${song.title}' locally inside 'extracted_songs' folder. Track duration: ${(song.durationMs / 1000)} seconds."
                },
                onFailure = { error ->
                    _isConverting.value = false
                    _conversionProgress.value = 0
                    _conversionLog.value = "Error during extraction: $error"
                }
            )
        }
    }

    // Google Drive Sync Actions
    fun updateDriveToken(token: String) {
        _driveAuthToken.value = token
    }

    fun syncDataWithDrive() {
        viewModelScope.launch {
            val songsList = allSongs.value
            val playlistsList = allPlaylists.value
            val token = _driveAuthToken.value.ifEmpty { null }
            
            syncManager.syncToGoogleDrive(
                songs = songsList,
                playlists = playlistsList,
                crossRefs = allCrossReferences,
                authToken = token
            )
        }
    }

    fun disconnectDrive() {
        _driveAuthToken.value = ""
        syncManager.disconnectDrive()
    }

    // Excluded folder helpers
    private fun loadExcludedFolders() {
        val raw = sharedPrefs.getString("excluded_folders", "System,WhatsApp,Telegram,Android") ?: "System,WhatsApp,Telegram,Android"
        _excludedFolders.value = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun addExcludedFolder(folderName: String) {
        val current = _excludedFolders.value.toMutableList()
        val trimmed = folderName.trim()
        if (trimmed.isNotEmpty() && !current.contains(trimmed)) {
            current.add(trimmed)
            _excludedFolders.value = current
            sharedPrefs.edit().putString("excluded_folders", current.joinToString(",")).apply()
        }
    }

    fun removeExcludedFolder(folderName: String) {
        val current = _excludedFolders.value.toMutableList()
        if (current.remove(folderName)) {
            _excludedFolders.value = current
            sharedPrefs.edit().putString("excluded_folders", current.joinToString(",")).apply()
        }
    }

    // Trigger explicit manual scan
    fun triggerManualScan() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val countBefore = db.musicDao().getSongsCount()
                val folders = _excludedFolders.value
                repository.scanDeviceForSongs(folders)
                val countAfter = db.musicDao().getSongsCount()
                val newlyAdded = (countAfter - countBefore).coerceAtLeast(0)
                _scannedCount.value = countAfter
                _newSongsAddedCount.value = newlyAdded
            } catch (e: Exception) {
                Log.e(TAG, "Manual scan failed", e)
            } finally {
                delay(1000) // visual touch for the scan screen
                _isScanning.value = false
            }
        }
    }

    fun dismissNewSongsDialog() {
        _newSongsAddedCount.value = null
    }

    // Rename song on disk & db
    fun renameSong(song: SongEntity, newTitle: String) {
        viewModelScope.launch {
            val updated = repository.renameSong(song, newTitle)
            if (updated != null) {
                Log.d(TAG, "Successfully renamed song metadata & file to: $newTitle")
            }
        }
    }

    // Playlist Sorting & Reordering Up / Down
    fun movePlaylistSongUp(song: SongEntity) {
        val current = _playlistSongs.value.toMutableList()
        val index = current.indexOfFirst { it.id == song.id }
        if (index > 0) {
            val temp = current[index]
            current[index] = current[index - 1]
            current[index - 1] = temp
            _playlistSongs.value = current
        }
    }

    fun movePlaylistSongDown(song: SongEntity) {
        val current = _playlistSongs.value.toMutableList()
        val index = current.indexOfFirst { it.id == song.id }
        if (index >= 0 && index < current.size - 1) {
            val temp = current[index]
            current[index] = current[index + 1]
            current[index + 1] = temp
            _playlistSongs.value = current
        }
    }

    fun sortPlaylistByTitle() {
        _playlistSongs.value = _playlistSongs.value.sortedBy { it.title.lowercase() }
    }

    fun sortPlaylistByDate() {
        _playlistSongs.value = _playlistSongs.value.sortedByDescending { it.dateAdded }
    }

    fun sortPlaylistBySize() {
        _playlistSongs.value = _playlistSongs.value.sortedByDescending { it.fileSize }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }

    // State flow helper extension inside VM
    private fun <T> kotlinx.coroutines.flow.Flow<T>.stateInViewModel(initialValue: T): StateFlow<T> {
        return this.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = initialValue
        )
    }
}
