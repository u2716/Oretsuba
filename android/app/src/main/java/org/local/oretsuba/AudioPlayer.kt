package org.local.oretsuba

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioPlayer(private val engine: Engine) {
  private var track: AudioTrack? = null
  private var thread: Thread? = null
  @Volatile private var running = false
  fun start() {
    if (running) return
    var rate = engine.audioRate()
    if (rate <= 0) rate = 44100
    val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
    val bufSize = if (minBuf > 0) minBuf * 4 else 16384
    val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    val fmt = AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
    val t = AudioTrack(attrs, fmt, bufSize, AudioTrack.MODE_STREAM, 0)
    track = t
    t.play()
    running = true
    thread = Thread {
      val frames = 1024
      val floatBuf = FloatArray(frames * 2)
      val shortBuf = ShortArray(frames * 2)
      while (running) {
        engine.audioMix(floatBuf, frames)
        for (i in shortBuf.indices) {
          var v = floatBuf[i]
          if (v > 1f) v = 1f
          if (v < -1f) v = -1f
          shortBuf[i] = (v * 32767f).toInt().toShort()
        }
        val tr = track
        if (tr == null) break
        tr.write(shortBuf, 0, shortBuf.size)
      }
    }
    thread?.start()
  }
  fun pause() {
    try {
      track?.pause()
    } catch (e: Exception) {
    }
  }
  fun resume() {
    try {
      track?.play()
    } catch (e: Exception) {
    }
  }
  fun stop() {
    running = false
    try {
      thread?.join(500)
    } catch (e: Exception) {
    }
    try {
      track?.stop()
    } catch (e: Exception) {
    }
    try {
      track?.release()
    } catch (e: Exception) {
    }
    track = null
    thread = null
  }
}
