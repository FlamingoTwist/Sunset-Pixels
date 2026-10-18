package com.retro.pixelplayer.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.retro.pixelplayer.R

/**
 * Handles clunky mechanical button vibrations and authentic audio sound effects.
 */
class MechanicalFeedback(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    private val soundPool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            .build()
    } catch (_: Throwable) {
        null
    }

    private var playSoundId: Int = 0
    private var stopSoundId: Int = 0
    private var rewindSoundId: Int = 0
    private var ejectSoundId: Int = 0

    init {
        try {
            soundPool?.let { sp ->
                playSoundId = sp.load(context, R.raw.sfx_play, 1)
                stopSoundId = sp.load(context, R.raw.sfx_stop, 1)
                rewindSoundId = sp.load(context, R.raw.sfx_rewind, 1)
                ejectSoundId = sp.load(context, R.raw.sfx_eject, 1)
            }
        } catch (_: Throwable) {
            // Safe fallback during Layoutlib / Compose Preview
        }
    }

    /**
     * Play button press sound and heavy vibration
     */
    fun onPlayPress() {
        vibrateHeavy()
        if (playSoundId != 0) {
            soundPool?.play(playSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Stop button press sound and heavy vibration
     */
    fun onStopPress() {
        vibrateHeavy()
        if (stopSoundId != 0) {
            soundPool?.play(stopSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Rewind or Fast-Forward button press sound and vibration
     */
    fun onRewindPress() {
        vibrateTick()
        if (rewindSoundId != 0) {
            soundPool?.play(rewindSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Tactile mechanical eject sound and double-pulse latch vibration
     */
    fun onTapeLatch() {
        vibrateDoublePulse()
        if (ejectSoundId != 0) {
            soundPool?.play(ejectSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    /**
     * Generic heavy button press
     */
    fun onHeavyButtonPress() {
        onStopPress()
    }

    /**
     * Subtle mechanical tick (switches, buttons)
     */
    fun onTick() {
        vibrateTick()
        if (rewindSoundId != 0) {
            soundPool?.play(rewindSoundId, 0.7f, 0.7f, 1, 0, 1.2f)
        }
    }

    private fun vibrateHeavy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(45L)
        }
    }

    private fun vibrateTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(15L)
        }
    }

    private fun vibrateDoublePulse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 35, 40, 60)
            val amplitudes = intArrayOf(0, 180, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(80L)
        }
    }

    fun release() {
        soundPool?.release()
    }
}
