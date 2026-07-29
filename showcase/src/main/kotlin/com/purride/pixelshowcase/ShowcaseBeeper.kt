package com.purride.pixelshowcase

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * 8-bit 蜂鸣器：方波直接合成 PCM，MODE_STATIC 一次写入反复触发。
 * 和画面同一哲学——没有音频资产，声音是程序生成的。
 */
class ShowcaseBeeper {

    /** 短促嘀嗒：勾选、删除这类轻操作。 */
    private val tick = StaticTone(squareWave(frequency = 1200f, durationMs = 30))

    /** 两音上行：开始、确认这类"事情启动了"的操作。 */
    private val confirm = StaticTone(
        squareWave(frequency = 660f, durationMs = 45) +
            squareWave(frequency = 990f, durationMs = 70),
    )

    /** 警报循环：两短一长加停顿，setLoopPoints 无限循环直到显式停止。 */
    private val alarm = StaticTone(
        squareWave(frequency = 880f, durationMs = 90) +
            silence(durationMs = 70) +
            squareWave(frequency = 880f, durationMs = 90) +
            silence(durationMs = 70) +
            squareWave(frequency = 1175f, durationMs = 160) +
            silence(durationMs = 420),
        loop = true,
    )

    fun tick() = tick.trigger()

    fun confirm() = confirm.trigger()

    fun alarmStart() = alarm.trigger()

    fun alarmStop() = alarm.halt()

    fun dispose() {
        tick.release()
        confirm.release()
        alarm.release()
    }

    /** 单段 PCM 的可重触发播放器。 */
    private class StaticTone(samples: ShortArray, private val loop: Boolean = false) {
        private val frameCount = samples.size
        private val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            frameCount * 2,
            AudioTrack.MODE_STATIC,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
        ).also { it.write(samples, 0, samples.size) }

        fun trigger() {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.stop()
            track.reloadStaticData()
            if (loop) track.setLoopPoints(0, frameCount, -1)
            track.play()
        }

        fun halt() {
            if (track.state != AudioTrack.STATE_INITIALIZED) return
            track.stop()
        }

        fun release() = track.release()
    }

    private companion object {
        const val SAMPLE_RATE = 22050
        const val AMPLITUDE = (Short.MAX_VALUE * 0.2f).toInt()

        /** 方波：相位按周期翻转，尾部 5ms 线性衰减防爆音。 */
        fun squareWave(frequency: Float, durationMs: Int): ShortArray {
            val total = SAMPLE_RATE * durationMs / 1000
            val halfPeriod = (SAMPLE_RATE / frequency / 2f).coerceAtLeast(1f)
            val fade = SAMPLE_RATE * 5 / 1000
            return ShortArray(total) { i ->
                val level = if ((i / halfPeriod).toInt() % 2 == 0) AMPLITUDE else -AMPLITUDE
                val remaining = total - i
                val scale = if (remaining < fade) remaining.toFloat() / fade else 1f
                (level * scale).toInt().toShort()
            }
        }

        fun silence(durationMs: Int): ShortArray = ShortArray(SAMPLE_RATE * durationMs / 1000)
    }
}
