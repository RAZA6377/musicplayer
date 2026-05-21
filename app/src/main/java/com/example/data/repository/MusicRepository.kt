package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.data.converter.VideoAudioExtractor
import com.example.data.db.*
import com.example.data.player.WavGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MusicRepository(private val context: Context, private val db: AppDatabase) {

    private val musicDao = db.musicDao()
    private val TAG = "MusicRepository"

    // Flows for Reactive UI updates
    val allSongs: Flow<List<SongEntity>> = musicDao.getAllSongs()
    val allPlaylists: Flow<List<PlaylistEntity>> = musicDao.getAllPlaylists()
    val allQueueItems: Flow<List<QueueItemEntity>> = musicDao.getQueueItems()

    fun getSongsInPlaylist(playlistId: String): Flow<List<SongEntity>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    // Preloads high quality local procedural tracks if no songs exist
    suspend fun preloadSampleSongs() = withContext(Dispatchers.IO) {
        val songsDir = File(context.filesDir, "preloaded_songs").apply {
            if (!exists()) mkdirs()
        }

        val count = musicDao.getSongsCount()
        if (count > 0) {
            Log.d(TAG, "Database already preloaded.")
            return@withContext
        }

        // Generate 3 gorgeous procedural ambient synth tracks
        val track1 = File(songsDir, "neon_after_hours.wav")
        val track2 = File(songsDir, "midnight_dreamer.wav")
        val track3 = File(songsDir, "hyper_sonic_rush.wav")

        WavGenerator.generateAmbientTrack(track1, durationSeconds = 12, mode = 0)
        WavGenerator.generateAmbientTrack(track2, durationSeconds = 12, mode = 1)
        WavGenerator.generateAmbientTrack(track3, durationSeconds = 12, mode = 2)

        val sampleSongs = listOf(
            SongEntity(
                id = "preloaded_1",
                title = "Neon After-Hours",
                artist = "Sonic Ambience",
                album = "Lofi Synths Vol. 1",
                durationMs = 12000L,
                filePath = track1.absolutePath,
                artUri = null,
                isPreloaded = true,
                fileSize = track1.length()
            ),
            SongEntity(
                id = "preloaded_2",
                title = "Midnight Dreamer",
                artist = "Sunset Wanderer",
                album = "Neon Dreams",
                durationMs = 12000L,
                filePath = track2.absolutePath,
                artUri = null,
                isPreloaded = true,
                fileSize = track2.length()
            ),
            SongEntity(
                id = "preloaded_3",
                title = "Hyper-Sonic Rush",
                artist = "Sub-Orbit Echo",
                album = "Space Rave",
                durationMs = 12000L,
                filePath = track3.absolutePath,
                artUri = null,
                isPreloaded = true,
                fileSize = track3.length()
            )
        )

        musicDao.insertSongs(sampleSongs)
        Log.d(TAG, "Preloaded sample tracks into Room Database")
    }

    // Custom music files importer
    suspend fun importAudioFile(uri: Uri): SongEntity? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                ?: getFileDisplayName(uri) ?: "Unknown Track"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown Album"
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            // Copy file content locally to internal directory
            val importedDir = File(context.filesDir, "imported_songs").apply {
                if (!exists()) mkdirs()
            }
            val uniqueId = UUID.randomUUID().toString()
            val fileExtension = getFileExtension(uri) ?: "mp3"
            val targetFile = File(importedDir, "$uniqueId.$fileExtension")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val song = SongEntity(
                id = uniqueId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                filePath = targetFile.absolutePath,
                artUri = null,
                isPreloaded = false,
                fileSize = targetFile.length()
            )

            musicDao.insertSong(song)
            return@withContext song
        } catch (e: Exception) {
            Log.e(TAG, "Error importing audio file: ${e.message}", e)
            return@withContext null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Video-to-audio extraction job
    suspend fun convertVideoToAudio(
        videoUri: Uri,
        name: String,
        onProgress: (Int) -> Unit,
        onSuccess: (SongEntity) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val uniqueId = UUID.randomUUID().toString()
        VideoAudioExtractor.extractAudio(
            context = context,
            videoUri = videoUri,
            outputFileName = uniqueId,
            listener = object : VideoAudioExtractor.ExtractionListener {
                override fun onProgress(percentage: Int) {
                    onProgress(percentage)
                }

                override fun onSuccess(audioFile: File) {
                    val retriever = MediaMetadataRetriever()
                    var durationMs = 12000L
                    try {
                        retriever.setDataSource(audioFile.absolutePath)
                        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 12000L
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }

                    val song = SongEntity(
                        id = uniqueId,
                        title = name,
                        artist = "Extracted Audio",
                        album = "Ex-Video Studio",
                        durationMs = durationMs,
                        filePath = audioFile.absolutePath,
                        isPreloaded = false,
                        fileSize = audioFile.length()
                    )

                    // Write to database via Room in IO coroutine
                    kotlinx.coroutines.MainScope().launch {
                        try {
                            withContext(Dispatchers.IO) {
                                musicDao.insertSong(song)
                            }
                            onSuccess(song)
                        } catch (e: Exception) {
                            Log.e("MusicRepository", "Failed database extraction insert", e)
                            onFailure("Database update failed")
                        }
                    }
                }

                override fun onFailure(error: String) {
                    onFailure(error)
                }
            }
        )
    }

    // CRUD functions for user adjustments
    suspend fun deleteSong(song: SongEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(song.filePath)
            if (file.exists() && !song.isPreloaded) {
                file.delete()
            }
            musicDao.deleteSong(song)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting song: ${song.title}", e)
        }
    }

    suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        try {
            val playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = name
            )
            musicDao.insertPlaylist(playlist)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating playlist: $name", e)
        }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        try {
            musicDao.deletePlaylistById(playlistId)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting playlist: $playlistId", e)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        try {
            musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
        } catch (e: Exception) {
            Log.e(TAG, "Error adding song $songId to playlist $playlistId", e)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        try {
            musicDao.deletePlaylistSongCrossRef(playlistId, songId)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing song $songId from playlist $playlistId", e)
        }
    }

    suspend fun refreshQueue(queueItems: List<QueueItemEntity>) = withContext(Dispatchers.IO) {
        try {
            musicDao.updateQueue(queueItems)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing queue", e)
        }
    }

    // Scan device folders for songs with support for exclusion
    suspend fun scanDeviceForSongs(excludedFolders: List<String>): List<SongEntity> = withContext(Dispatchers.IO) {
        val detected = mutableListOf<SongEntity>()
        
        // Scan standard music & downloads folders
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val filesDir = context.getExternalFilesDir(null)
        val internalFilesDir = context.filesDir

        val roots = listOfNotNull(filesDir, internalFilesDir, musicDir, downloadsDir)
        
        for (root in roots) {
            if (root.exists() && root.isDirectory) {
                scanDirRecursive(root, excludedFolders, detected)
            }
        }
        
        if (detected.isNotEmpty()) {
            musicDao.insertSongs(detected)
        }
        detected
    }

    private fun scanDirRecursive(dir: File, excludedFolders: List<String>, result: MutableList<SongEntity>) {
        if (excludedFolders.any { dir.absolutePath.contains(it) || dir.name.equals(it, ignoreCase = true) }) {
            Log.d(TAG, "Skipping excluded directory: ${dir.absolutePath}")
            return
        }
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDirRecursive(file, excludedFolders, result)
            } else if (file.isFile && (file.extension.equals("mp3", true) || file.extension.equals("wav", true) || file.extension.equals("m4a", true))) {
                val path = file.absolutePath
                val songId = "scanned_${path.hashCode()}"
                
                var title = file.nameWithoutExtension
                var artist = "Local Folder"
                var album = dir.name
                var durationMs = 0L
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: artist
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 12000L
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
                
                result.add(
                    SongEntity(
                        id = songId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        filePath = path,
                        artUri = null,
                        isPreloaded = false,
                        dateAdded = file.lastModified(),
                        fileSize = file.length()
                    )
                )
            }
        }
    }

    // Rename song on disk and update Room DB
    suspend fun renameSong(song: SongEntity, newTitle: String): SongEntity? = withContext(Dispatchers.IO) {
        try {
            if (song.isPreloaded) {
                val updated = song.copy(title = newTitle)
                musicDao.insertSong(updated)
                return@withContext updated
            }
            
            val originalFile = File(song.filePath)
            if (originalFile.exists()) {
                val parentDir = originalFile.parentFile
                val extension = originalFile.extension
                val sanitizedTitle = newTitle.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "")
                val newFile = File(parentDir, "$sanitizedTitle.$extension")
                
                if (originalFile.renameTo(newFile)) {
                    val updated = song.copy(
                        title = newTitle,
                        filePath = newFile.absolutePath,
                        dateAdded = System.currentTimeMillis()
                    )
                    musicDao.insertSong(updated)
                    return@withContext updated
                }
            } else {
                val updated = song.copy(title = newTitle)
                musicDao.insertSong(updated)
                return@withContext updated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed renaming song physical/DB metadata", e)
        }
        null
    }

    // Helpers
    private fun getFileDisplayName(uri: Uri): String? {
        return uri.path?.split("/")?.lastOrNull() ?: "Custom import"
    }

    private fun getFileExtension(uri: Uri): String? {
        val path = uri.path ?: return null
        val dot = path.lastIndexOf('.')
        return if (dot != -1) path.substring(dot + 1) else null
    }
}
