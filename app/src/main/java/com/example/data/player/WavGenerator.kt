package com.example.data.player

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object WavGenerator {

    private const val TAG = "WavGenerator"
    private const val SAMPLE_RATE = 44100
    private const val CHANNELS = 2
    private const val BITS_PER_SAMPLE = 16

    /**
     * Synthesizes a beautiful relaxing ambient sound (15 seconds) and writes it as a valid WAV.
     * This runs 100% offline and ensures the player has gorgeous, high-faith test waveforms!
     * Progression: C Major 7 (C, E, G, B) to F Major 7 (F, A, C, E)
     */
    fun generateAmbientTrack(outputFile: File, durationSeconds: Int = 12, mode: Int = 0): Boolean {
        if (outputFile.exists()) {
            return true // Already created!
        }

        try {
            val totalSamples = SAMPLE_RATE * durationSeconds
            val frameSize = CHANNELS * (BITS_PER_SAMPLE / 8)
            val subChunk2Size = totalSamples * frameSize
            val chunkSize = 36 + subChunk2Size

            FileOutputStream(outputFile).use { fos ->
                // Write WAV Header
                fos.write("RIFF".toByteArray())             // ChunkID
                fos.write(intToByteArray(chunkSize))         // ChunkSize (little endian)
                fos.write("WAVE".toByteArray())             // Format
                fos.write("fmt ".toByteArray())             // Subchunk1ID
                fos.write(intToByteArray(16))               // Subchunk1Size (16 for PCM)
                fos.write(shortToByteArray(1))              // AudioFormat (1 for PCM)
                fos.write(shortToByteArray(CHANNELS.toShort())) // NumChannels
                fos.write(intToByteArray(SAMPLE_RATE))       // SampleRate
                fos.write(intToByteArray(SAMPLE_RATE * frameSize)) // ByteRate
                fos.write(shortToByteArray(frameSize.toShort())) // BlockAlign
                fos.write(shortToByteArray(BITS_PER_SAMPLE.toShort())) // BitsPerSample
                fos.write("data".toByteArray())             // Subchunk2ID
                fos.write(intToByteArray(subChunk2Size))     // Subchunk2Size

                // Define frequencies based on mode
                // mode 0: Chill Lofi (Cmaj7 to Fmaj7)
                // mode 1: Neo Dreams (Am9 to G)
                // mode 2: Cosmic Synth Speed (E minor driving chord)
                val chords = when (mode) {
                    0 -> listOf(
                        listOf(130.81, 164.81, 196.00, 246.94), // C3, E3, G3, B3
                        listOf(174.61, 220.00, 261.63, 329.63)  // F3, A3, C4, E4
                    )
                    1 -> listOf(
                        listOf(220.00, 261.63, 329.63, 392.00, 493.88), // A3, C4, E4, G4, B4
                        listOf(196.00, 246.94, 293.66, 392.00)          // G3, B3, D4, G4
                    )
                    else -> listOf(
                        listOf(164.81, 246.94, 329.63, 440.00), // E3, B3, E4, A4
                        listOf(146.83, 220.00, 293.66, 392.00)  // D3, A3, D4, G4
                    )
                }

                val buffer = ByteBuffer.allocate(4096).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                }

                val chordDurationSamples = totalSamples / chords.size

                for (s in 0 until totalSamples) {
                    val chordIdx = (s / chordDurationSamples).coerceIn(chords.indices)
                    val frequencies = chords[chordIdx]
                    
                    var sampleValL = 0.0
                    var sampleValR = 0.0
                    val t = s.toDouble() / SAMPLE_RATE

                    // Generate thick layered ambient waves representing slowed & reverb vibe
                    for (i in frequencies.indices) {
                        val freq = frequencies[i]
                        // Simple Sine Wave with beautiful spatial movement and vibrato
                        val vibrato = 1.0 + 0.012 * sin(2.0 * Math.PI * 4.5 * t)
                        val angle = 2.0 * Math.PI * freq * vibrato * t
                        var wave = sin(angle)
                        
                        // Add some beautiful tape saturation / third harmonic
                        wave += 0.15 * sin(angle * 3.0)
                        
                        // Spatial panning (slowly sweep each note across stereo image)
                        val panSpeed = 0.15 + (i * 0.08)
                        val pan = 0.5 + 0.35 * sin(2.0 * Math.PI * panSpeed * t)
                        
                        sampleValL += wave * (1.0 - pan)
                        sampleValR += wave * pan
                    }

                    // Master volume gain control
                    val volume = 0.18
                    
                    // Fade in at the start, fade out at the end
                    val fadeInFactor = if (s < SAMPLE_RATE) s.toDouble() / SAMPLE_RATE else 1.0
                    val fadeOutFactor = if (s > totalSamples - SAMPLE_RATE) (totalSamples - s).toDouble() / SAMPLE_RATE else 1.0
                    val shape = fadeInFactor * fadeOutFactor

                    val valShortL = (sampleValL / frequencies.size * volume * shape * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    val valShortR = (sampleValR / frequencies.size * volume * shape * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                    if (buffer.remaining() < 4) {
                        buffer.flip()
                        fos.write(buffer.array(), 0, buffer.limit())
                        buffer.clear()
                    }

                    buffer.putShort(valShortL.toShort())
                    buffer.putShort(valShortR.toShort())
                }

                if (buffer.position() > 0) {
                    buffer.flip()
                    fos.write(buffer.array(), 0, buffer.limit())
                }
            }
            Log.d(TAG, "Successfully synthesized offline high fidelity WAV: ${outputFile.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed synthesizing offline test songs", e)
            return false
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            (value shr 8 and 0xff).toByte(),
            (value shr 16 and 0xff).toByte(),
            (value shr 24 and 0xff).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xff).toByte(),
            (value.toInt() shr 8 and 0xff).toByte()
        )
    }
}
