package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val filePath: String, // Can be local file absolute path, raw asset, or content URI
    val artUri: String? = null,
    val isPreloaded: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_song_cross_ref", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String
)

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Int = 0,
    val songId: String,
    val orderIndex: Int
)
