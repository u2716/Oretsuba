package org.local.oretsuba

import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer

class Engine {
  companion object {
    init {
      System.loadLibrary("oretsuba")
    }
  }
  interface Listener {
    fun onDialogRequested(text: String, kind: Int)
    fun onTextInputRequested(initial: String)
    fun onStopped(error: String?)
  }
  var listener: Listener? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  var handle: Long = 0L
    private set
  var createError: String = ""
    private set
  fun create(dataDir: String, patchDir: String?, saveDir: String, chinese: Boolean, helper: PlatformHelper): Boolean {
    handle = nativeCreate(dataDir, patchDir, saveDir, chinese, helper)
    if (handle == 0L) {
      createError = nativeCreateError()
      return false
    }
    nativeSetUiCallbacks(handle)
    return true
  }
  fun destroy() {
    if (handle != 0L) {
      nativeDestroy(handle)
      handle = 0L
    }
  }
  fun frame(nowMs: Long): Int {
    if (handle == 0L) return -1
    return nativeFrame(handle, nowMs)
  }
  fun lastError(): String {
    if (handle == 0L) return ""
    return nativeLastError(handle)
  }
  fun frameSerial(): Long {
    if (handle == 0L) return 0L
    return nativeFrameSerial(handle)
  }
  fun framebufferSize(out: IntArray): Boolean {
    if (handle == 0L) return false
    return nativeFramebufferSize(handle, out)
  }
  fun copyFramebuffer(dst: ByteBuffer): Boolean {
    if (handle == 0L) return false
    return nativeCopyFramebuffer(handle, dst)
  }
  fun audioRate(): Int {
    return nativeAudioRate()
  }
  fun audioMix(out: FloatArray, frames: Int) {
    if (handle == 0L) return
    nativeAudioMix(handle, out, frames)
  }
  fun mouseMove(x: Int, y: Int) {
    if (handle == 0L) return
    nativeMouseMove(handle, x, y)
  }
  fun mouseButton(button: Int, down: Boolean) {
    if (handle == 0L) return
    nativeMouseButton(handle, button, down)
  }
  fun mouseWheel(delta: Int) {
    if (handle == 0L) return
    nativeMouseWheel(handle, delta)
  }
  fun key(vk: Int, down: Boolean) {
    if (handle == 0L) return
    nativeKey(handle, vk, down)
  }
  fun gameCommand(cmd: Int) {
    if (handle == 0L) return
    nativeGameCommand(handle, cmd)
  }
  fun setPaused(paused: Boolean) {
    if (handle == 0L) return
    nativeSetPaused(handle, paused)
  }
  fun answerDialog(result: Int) {
    if (handle == 0L) return
    nativeAnswerDialog(handle, result)
  }
  fun answerTextInput(ok: Boolean, text: String?) {
    if (handle == 0L) return
    nativeAnswerTextInput(handle, ok, text)
  }
  fun onDialogRequested(text: String, kind: Int) {
    mainHandler.post { listener?.onDialogRequested(text, kind) }
  }
  fun onTextInputRequested(initial: String) {
    mainHandler.post { listener?.onTextInputRequested(initial) }
  }
  private external fun nativeCreate(dataDir: String, patchDir: String?, saveDir: String, chinese: Boolean, helper: PlatformHelper): Long
  private external fun nativeCreateError(): String
  private external fun nativeDestroy(handle: Long)
  private external fun nativeFrame(handle: Long, nowMs: Long): Int
  private external fun nativeLastError(handle: Long): String
  private external fun nativeFrameSerial(handle: Long): Long
  private external fun nativeFramebufferSize(handle: Long, out: IntArray): Boolean
  private external fun nativeCopyFramebuffer(handle: Long, dst: ByteBuffer): Boolean
  private external fun nativeAudioRate(): Int
  private external fun nativeAudioMix(handle: Long, out: FloatArray, frames: Int)
  private external fun nativeMouseMove(handle: Long, x: Int, y: Int)
  private external fun nativeMouseButton(handle: Long, button: Int, down: Boolean)
  private external fun nativeMouseWheel(handle: Long, delta: Int)
  private external fun nativeKey(handle: Long, vk: Int, down: Boolean)
  private external fun nativeGameCommand(handle: Long, command: Int)
  private external fun nativeSetPaused(handle: Long, paused: Boolean)
  private external fun nativeAnswerDialog(handle: Long, result: Int)
  private external fun nativeAnswerTextInput(handle: Long, ok: Boolean, text: String?)
  private external fun nativeSetUiCallbacks(handle: Long)
}
