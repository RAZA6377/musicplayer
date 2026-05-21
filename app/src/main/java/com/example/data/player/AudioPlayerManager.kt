package com.example.data.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.PresetReverb
import android.media.PlaybackParams
import android.net.Uri
import android.util.Log
import com.example.data.db.SongEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayerManager private constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var presetReverb: PresetReverb? = null
    
    // Playback state flows
    private val _currentSong = MutableStateFlow<SongEntity?>(null)
    val currentSong: StateFlow<SongEntity?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    // Slowed & Reverb Configuration
    private val _speedVal = MutableStateFlow(1.0f)
    val speedVal: StateFlow<Float> = _speedVal.asStateFlow()

    private val _pitchVal = MutableStateFlow(1.0f)
    val pitchVal: StateFlow<Float> = _pitchVal.asStateFlow()

    private val _isReverbEnabled = MutableStateFlow(false)
    val isReverbEnabled: StateFlow<Boolean> = _isReverbEnabled.asStateFlow()

    private val _reverbPreset = MutableStateFlow(PresetReverb.PRESET_LARGEHALL.toInt())
    val reverbPreset: StateFlow<Int> = _reverbPreset.asStateFlow()

    // Queue system
    private val _playingQueue = MutableStateFlow<List<SongEntity>>(emptyList())
    val playingQueue: StateFlow<List<SongEntity>> = _playingQueue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeatAll = MutableStateFlow(false)
    val isRepeatAll: StateFlow<Boolean> = _isRepeatAll.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressTrackerJob: Job? = null

    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerManager? = null

        fun getInstance(context: Context): AudioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AudioPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnCompletionListener {
                    handleSongCompletion()
                }
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration.toLong()
                    mp.start()
                    _isPlaying.value = true
                    applyAudioEffects()
                    startProgressTracking()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "MediaPlayer Error: what=$what, extra=$extra")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Initialization failed", e)
        }
    }

    // Load and play a specific song
    fun playSong(song: SongEntity) {
        try {
            if (mediaPlayer == null) {
                initializePlayer()
            }
            _currentSong.value = song
            _playbackProgress.value = 0L
            stopProgressTracking()

            mediaPlayer?.reset()
            
            // Set data source based on file or raw asset url
            if (song.filePath.startsWith("android.resource://")) {
                val uri = Uri.parse(song.filePath)
                mediaPlayer?.setDataSource(context, uri)
            } else {
                val file = File(song.filePath)
                if (file.exists() && file.isFile) {
                    mediaPlayer?.setDataSource(file.absolutePath)
                } else if (song.filePath.startsWith("content://") || song.filePath.startsWith("file://")) {
                    mediaPlayer?.setDataSource(context, Uri.parse(song.filePath))
                } else {
                    // Fallback stream or direct loading from assets
                    try {
                        val descriptor = context.assets.openFd(song.filePath)
                        mediaPlayer?.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                        descriptor.close()
                    } catch (e: Exception) {
                        // Stream online URL or direct database file
                        mediaPlayer?.setDataSource(song.filePath)
                    }
                }
            }

            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error loading song: ${song.title}", e)
        }
    }

    fun play() {
        try {
            if (_currentSong.value != null && mediaPlayer != null) {
                mediaPlayer?.start()
                _isPlaying.value = true
                applyAudioEffects()
                startProgressTracking()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Play failed", e)
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            _isPlaying.value = false
            stopProgressTracking()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Pause failed", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _playbackProgress.value = positionMs
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Seek failed", e)
        }
    }

    // Controls Slowed dynamic speed and pitch values
    fun setPlaybackParams(speed: Float, pitch: Float) {
        _speedVal.value = speed
        _pitchVal.value = pitch
        applyDynamicPlaybackParams()
    }

    private fun applyDynamicPlaybackParams() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying || _isPlaying.value) {
                    val params = PlaybackParams().apply {
                        speed = _speedVal.value
                        pitch = _pitchVal.value
                    }
                    mp.playbackParams = params
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Setting PlaybackParams failed, speed=${_speedVal.value}", e)
        }
    }

    // Dynamic Reverb Adjuster
    fun setReverbEnabled(enabled: Boolean) {
        _isReverbEnabled.value = enabled
        applyReverbConfig()
    }

    fun setReverbPreset(preset: Int) {
        _reverbPreset.value = preset
        applyReverbConfig()
    }

    private fun applyAudioEffects() {
        applyDynamicPlaybackParams()
        applyReverbConfig()
    }

    private fun applyReverbConfig() {
        mediaPlayer?.let { mp ->
            try {
                if (presetReverb != null) {
                    presetReverb?.release()
                    presetReverb = null
                }
                if (_isReverbEnabled.value) {
                    presetReverb = PresetReverb(0, mp.audioSessionId).apply {
                        preset = _reverbPreset.value.toShort()
                        enabled = true
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Applying PresetReverb failed. Session ID=${mp.audioSessionId}", e)
            }
        }
    }

    // Queue API
    fun setQueue(songs: List<SongEntity>, startIndex: Int) {
        _playingQueue.value = songs
        _queueIndex.value = startIndex
        if (startIndex in songs.indices) {
            playSong(songs[startIndex])
        }
    }

    fun addToQueueNext(song: SongEntity) {
        val current = _playingQueue.value.toMutableList()
        val index = _queueIndex.value
        if (index == -1) {
            setQueue(listOf(song), 0)
        } else {
            // Check if already exists in next slot, remove duplicates to make it clean
            current.remove(song)
            current.add(index + 1, song)
            _playingQueue.value = current
        }
    }

    fun addToQueueEnd(song: SongEntity) {
        val current = _playingQueue.value.toMutableList()
        if (current.isEmpty()) {
            setQueue(listOf(song), 0)
        } else {
            current.remove(song)
            current.add(song)
            _playingQueue.value = current
        }
    }

    fun removeFromQueue(index: Int) {
        val current = _playingQueue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            val activeIndex = _queueIndex.value
            _playingQueue.value = current
            when {
                current.isEmpty() -> {
                    _queueIndex.value = -1
                    pause()
                    _currentSong.value = null
                }
                activeIndex == index -> {
                    // Playing item was removed: play the next item (or clamp to end)
                    val nextIdx = if (index >= current.size) current.size - 1 else index
                    _queueIndex.value = nextIdx
                    playSong(current[nextIdx])
                }
                activeIndex > index -> {
                    _queueIndex.value = activeIndex - 1
                }
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val current = _playingQueue.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val element = current.removeAt(fromIndex)
            current.add(toIndex, element)
            
            // Adjust current index
            val activeIndex = _queueIndex.value
            val newIdx = when {
                activeIndex == fromIndex -> toIndex
                activeIndex in (fromIndex + 1)..toIndex -> activeIndex - 1
                activeIndex in toIndex..<(fromIndex) -> activeIndex + 1
                else -> activeIndex
            }
            _playingQueue.value = current
            _queueIndex.value = newIdx
        }
    }

    fun playNext() {
        val queue = _playingQueue.value
        if (queue.isEmpty()) return

        if (_isShuffle.value) {
            val nextIdx = (queue.indices).random()
            _queueIndex.value = nextIdx
            playSong(queue[nextIdx])
        } else {
            val nextIdx = _queueIndex.value + 1
            if (nextIdx in queue.indices) {
                _queueIndex.value = nextIdx
                playSong(queue[nextIdx])
            } else if (_isRepeatAll.value) {
                _queueIndex.value = 0
                playSong(queue[0])
            } else {
                pause()
                seekTo(0)
            }
        }
    }

    fun playPrevious() {
        val queue = _playingQueue.value
        if (queue.isEmpty()) return

        val prevIdx = _queueIndex.value - 1
        if (prevIdx in queue.indices) {
            _queueIndex.value = prevIdx
            playSong(queue[prevIdx])
        } else if (_isRepeatAll.value && queue.isNotEmpty()) {
            _queueIndex.value = queue.size - 1
            playSong(queue[queue.size - 1])
        } else {
            seekTo(0)
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _isRepeatAll.value = !_isRepeatAll.value
    }

    private fun handleSongCompletion() {
        playNext()
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressTrackerJob = coroutineScope.launch {
            while (isActive) {
                try {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            _playbackProgress.value = mp.currentPosition.toLong()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient exceptions from player states
                }
                delay(150)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackerJob?.cancel()
        progressTrackerJob = null
    }

    fun release() {
        stopProgressTracking()
        try {
            if (presetReverb != null) {
                presetReverb?.release()
                presetReverb = null
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Release failed", e)
        }
    }
}
