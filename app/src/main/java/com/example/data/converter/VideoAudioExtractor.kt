package com.example.data.converter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoAudioExtractor {

    private const val TAG = "VideoAudioExtractor"

    interface ExtractionListener {
        fun onProgress(percentage: Int)
        fun onSuccess(audioFile: File)
        fun onFailure(error: String)
    }

    /**
     * Extracts the raw AAC audio track from a video file (MP4/3GP/MKV) and bundles it into an M4A file natively.
     * This is 100% offline, incredibly fast, and runs without transcoding overhead.
     */
    suspend fun extractAudio(
        context: Context,
        videoUri: Uri,
        outputFileName: String,
        listener: ExtractionListener
    ) = withContext(Dispatchers.IO) {
        
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, videoUri, null)

            // 1. Locate the audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            val trackCount = extractor.trackCount
            
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                listener.onFailure("No audio track found in the selected video")
                return@withContext
            }

            extractor.selectTrack(audioTrackIndex)

            // 2. Prep Output Target
            val outputDirectory = File(context.filesDir, "extracted_songs").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(outputDirectory, "$outputFileName.m4a")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            // 3. Configure Muxer to export in MPEG-4 (M4A) format
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            // 4. Extraction Loop
            val durationUs = audioFormat.getLong(MediaFormat.KEY_DURATION)
            val bufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).let { if (it <= 0) 1024 * 512 else it }
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            
            var bytesRead: Int
            var sampleTime: Long
            
            while (true) {
                bufferInfo.offset = 0
                bytesRead = extractor.readSampleData(buffer, 0)
                
                if (bytesRead < 0) {
                    break // EOF
                }

                sampleTime = extractor.sampleTime
                bufferInfo.size = bytesRead
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                
                // Track Progress
                if (durationUs > 0) {
                    val progress = ((sampleTime.toFloat() / durationUs.toFloat()) * 100).toInt()
                    withContext(Dispatchers.Main) {
                        listener.onProgress(progress.coerceIn(0, 100))
                    }
                }
                
                extractor.advance()
            }

            // Clean-up Muxer
            muxer.stop()
            
            withContext(Dispatchers.Main) {
                listener.onProgress(100)
                listener.onSuccess(outputFile)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Audio extraction failed", e)
            withContext(Dispatchers.Main) {
                listener.onFailure(e.localizedMessage ?: "Unknown hardware extraction error")
            }
        } finally {
            try {
                extractor?.release()
                muxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Releasing decoders failed", e)
            }
        }
    }
}
