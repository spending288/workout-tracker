package com.example.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object FeedbackUtils {

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerLightTick(context: Context) {
        runCatching {
            val vibrator = resolveVibrator(context)
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(12L)
                }
            }
        }
    }

    /**
     * Strong two-pulse pattern (300 ms buzz, 180 ms gap, 300 ms buzz) designed to be felt
     * in a pocket when a rest timer ends.
     */
    fun triggerRestCompleteVibration(context: Context) {
        runCatching {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val timings = longArrayOf(0L, 300L, 180L, 300L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255)
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }

    fun isReducedMotionEnabled(context: Context): Boolean {
        return runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }

    /**
     * Synthesizes a clean two-tone sine chime:
     * 660 Hz at t=0, 880 Hz at t=140ms stagger, each with 280ms exponential decay.
     * Requests transient audio focus with ducking so it plays cleanly over music.
     */
    suspend fun playRestCompleteChime(context: Context) = withContext(Dispatchers.Default) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            var focusRequest: AudioFocusRequest? = null
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .build()
                    focusRequest = req
                    audioManager.requestAudioFocus(req)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.requestAudioFocus(
                        null,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                }
            }

            try {
                val sampleRate = 22050
                val totalDurationSec = 0.55
                val numSamples = (sampleRate * totalDurationSec).toInt()
                val pcm = ShortArray(numSamples)

                val staggerSec = 0.140
                val decaySec = 0.280

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    var sample = 0.0
                    if (t < decaySec * 1.5) {
                        val env1 = exp(-t / (decaySec * 0.35))
                        sample += 0.45 * env1 * sin(2.0 * PI * 660.0 * t)
                    }
                    if (t >= staggerSec) {
                        val t2 = t - staggerSec
                        if (t2 < decaySec * 1.5) {
                            val env2 = exp(-t2 / (decaySec * 0.35))
                            sample += 0.48 * env2 * sin(2.0 * PI * 880.0 * t2)
                        }
                    }
                    val clamped = (sample * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    pcm[i] = clamped.toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()

                try {
                    audioTrack.write(pcm, 0, pcm.size)
                    audioTrack.play()
                    kotlinx.coroutines.delay(600L)
                    audioTrack.stop()
                } finally {
                    audioTrack.release()
                }
            } finally {
                if (audioManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.abandonAudioFocus(null)
                    }
                }
            }
        }
    }
}
