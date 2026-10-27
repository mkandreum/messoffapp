package com.messoff.pulso.haptics

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class Haptic {
    Selection, Light, Medium, Heavy, Success, Warning, Error, Tick, Snap, Swipe, Heartbeat, Celebrate
}

/**
 * El lenguaje táctil de Pulso en nativo. En móviles con motor háptico moderno
 * (Android 11+) usa primitivas compuestas —clics nítidos, golpes secos y subidas—;
 * en el resto, formas de onda con amplitud.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled = true
    private var lastTick = 0L

    val supported: Boolean get() = vibrator?.hasVibrator() == true

    private val rich: Boolean by lazy {
        Build.VERSION.SDK_INT >= 30 && vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
        ) == true
    }

    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 40) return
        lastTick = now
        play(Haptic.Tick)
    }

    fun play(kind: Haptic) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            val effect = if (rich && Build.VERSION.SDK_INT >= 30) composed(kind) else waveform(kind)
            v.vibrate(effect)
        }
    }

    private fun composed(kind: Haptic): VibrationEffect {
        val c = VibrationEffect.startComposition()
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val thud = VibrationEffect.Composition.PRIMITIVE_THUD
        val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        when (kind) {
            Haptic.Selection -> c.addPrimitive(tick, 0.7f)
            Haptic.Light -> c.addPrimitive(click, 0.45f)
            Haptic.Medium -> c.addPrimitive(click, 0.8f)
            Haptic.Heavy -> c.addPrimitive(thud, 1f)
            Haptic.Success -> c.addPrimitive(click, 0.5f).addPrimitive(click, 1f, 70)
            Haptic.Warning -> c.addPrimitive(click, 0.9f).addPrimitive(click, 0.6f, 110)
            Haptic.Error -> c.addPrimitive(thud, 0.9f).addPrimitive(click, 0.9f, 50).addPrimitive(click, 0.9f, 50)
            Haptic.Tick -> c.addPrimitive(tick, 0.5f)
            Haptic.Snap -> c.addPrimitive(thud, 0.85f).addPrimitive(tick, 0.5f, 40)
            Haptic.Swipe -> c.addPrimitive(click, 0.7f)
            Haptic.Heartbeat -> c.addPrimitive(click, 0.8f).addPrimitive(thud, 1f, 100)
            Haptic.Celebrate -> c.addPrimitive(rise, 0.6f).addPrimitive(click, 0.7f, 60).addPrimitive(click, 1f, 60)
        }
        return c.compose()
    }

    private fun waveform(kind: Haptic): VibrationEffect {
        fun wave(timings: LongArray, amps: IntArray) = VibrationEffect.createWaveform(timings, amps, -1)
        return when (kind) {
            Haptic.Selection -> VibrationEffect.createOneShot(8, 90)
            Haptic.Light -> VibrationEffect.createOneShot(14, 110)
            Haptic.Medium -> VibrationEffect.createOneShot(22, 180)
            Haptic.Heavy -> VibrationEffect.createOneShot(32, 255)
            Haptic.Success -> wave(longArrayOf(0, 18, 60, 26), intArrayOf(0, 140, 0, 255))
            Haptic.Warning -> wave(longArrayOf(0, 30, 90, 30), intArrayOf(0, 200, 0, 150))
            Haptic.Error -> wave(longArrayOf(0, 30, 50, 30, 50, 30), intArrayOf(0, 230, 0, 230, 0, 230))
            Haptic.Tick -> VibrationEffect.createOneShot(6, 80)
            Haptic.Snap -> wave(longArrayOf(0, 22, 36, 12), intArrayOf(0, 230, 0, 100))
            Haptic.Swipe -> VibrationEffect.createOneShot(14, 170)
            Haptic.Heartbeat -> wave(longArrayOf(0, 18, 90, 28), intArrayOf(0, 200, 0, 255))
            Haptic.Celebrate -> wave(longArrayOf(0, 16, 50, 16, 50, 40), intArrayOf(0, 120, 0, 180, 0, 255))
        }
    }
}
