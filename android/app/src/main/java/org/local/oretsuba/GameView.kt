package org.local.oretsuba

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class GameView(context: Context, private val engine: Engine, private val onStop: (String?) -> Unit) : GLSurfaceView(context) {
  private val renderer = GameRenderer()
  private var downX = 0f
  private var downY = 0f
  private var longPressFired = false
  private var isPan = false
  private var lastPanY = 0f
  private var wheelAccum = 0f
  private var twoFinger = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private val longPressRunnable = Runnable {
    longPressFired = true
    engine.key(0x11, true)
  }
  init {
    setEGLContextClientVersion(2)
    setRenderer(renderer)
    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    preserveEGLContextOnPause = true
    isFocusable = true
    isFocusableInTouchMode = true
    requestFocus()
  }
  fun sendCommand(cmd: Int) {
    engine.gameCommand(cmd)
  }
  fun gamePoint(viewX: Float, viewY: Float): Pair<Int, Int> {
    val r = renderer.contentRect
    val gw = renderer.gameW.toFloat()
    val gh = renderer.gameH.toFloat()
    if (r[2] <= 0 || r[3] <= 0) return Pair(0, 0)
    var x = (viewX - r[0]) / r[2] * gw
    var y = (viewY - r[1]) / r[3] * gh
    x = min(max(x, 0f), gw - 1)
    y = min(max(y, 0f), gh - 1)
    return Pair(x.toInt(), y.toInt())
  }
  private fun click(button: Int, x: Float, y: Float) {
    val p = gamePoint(x, y)
    engine.mouseMove(p.first, p.second)
    engine.mouseButton(button, true)
    mainHandler.postDelayed({ engine.mouseButton(button, false) }, 50)
  }
  override fun onTouchEvent(e: MotionEvent): Boolean {
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = e.x
        downY = e.y
        longPressFired = false
        isPan = false
        twoFinger = false
        lastPanY = e.y
        wheelAccum = 0f
        mainHandler.postDelayed(longPressRunnable, 600)
        return true
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        if (e.pointerCount == 2) {
          twoFinger = true
          mainHandler.removeCallbacks(longPressRunnable)
        }
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (twoFinger) return true
        val dx = e.x - downX
        val totalDy = e.y - downY
        if (!isPan && abs(totalDy) > 20 && abs(totalDy) > abs(dx)) {
          isPan = true
          mainHandler.removeCallbacks(longPressRunnable)
        }
        if (isPan) {
          val dy = e.y - lastPanY
          lastPanY = e.y
          wheelAccum += dy
          while (abs(wheelAccum) >= 40) {
            engine.mouseWheel(if (wheelAccum > 0) 120 else -120)
            wheelAccum -= if (wheelAccum > 0) 40f else -40f
          }
        } else {
          lastPanY = e.y
        }
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        mainHandler.removeCallbacks(longPressRunnable)
        if (longPressFired) {
          engine.key(0x11, false)
          longPressFired = false
        } else if (twoFinger) {
          click(1, e.x, e.y)
        } else if (!isPan) {
          click(0, e.x, e.y)
        }
        isPan = false
        twoFinger = false
        return true
      }
    }
    return super.onTouchEvent(e)
  }
  override fun onHoverEvent(e: MotionEvent): Boolean {
    if (e.action == MotionEvent.ACTION_HOVER_MOVE) {
      val p = gamePoint(e.x, e.y)
      engine.mouseMove(p.first, p.second)
      return true
    }
    return super.onHoverEvent(e)
  }
  override fun onGenericMotionEvent(e: MotionEvent): Boolean {
    if (e.isFromSource(android.view.InputDevice.SOURCE_MOUSE)) {
      when (e.action) {
        MotionEvent.ACTION_HOVER_MOVE -> {
          val p = gamePoint(e.x, e.y)
          engine.mouseMove(p.first, p.second)
          return true
        }
        MotionEvent.ACTION_SCROLL -> {
          val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
          if (v != 0f) {
            engine.mouseWheel(if (v > 0) 120 else -120)
          }
          return true
        }
      }
    }
    if (e.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK) || e.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD)) {
      val y = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
      if (y < -0.5f) engine.mouseWheel(120)
      else if (y > 0.5f) engine.mouseWheel(-120)
      return true
    }
    return super.onGenericMotionEvent(e)
  }
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val vk = mapKey(keyCode)
    if (vk != null) {
      engine.key(vk, true)
      return true
    }
    if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
      click(0, width / 2f, height / 2f)
      return true
    }
    if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
      click(1, width / 2f, height / 2f)
      return true
    }
    if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
      engine.key(0x11, true)
      return true
    }
    return super.onKeyDown(keyCode, event)
  }
  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    val vk = mapKey(keyCode)
    if (vk != null) {
      engine.key(vk, false)
      return true
    }
    if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
      engine.key(0x11, false)
      return true
    }
    return super.onKeyUp(keyCode, event)
  }
  private fun mapKey(keyCode: Int): Int? {
    return when (keyCode) {
      KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 0x0D
      KeyEvent.KEYCODE_SPACE -> 0x20
      KeyEvent.KEYCODE_ESCAPE -> 0x1B
      KeyEvent.KEYCODE_DPAD_UP -> 0x26
      KeyEvent.KEYCODE_DPAD_DOWN -> 0x28
      KeyEvent.KEYCODE_DPAD_LEFT -> 0x25
      KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27
      KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 0x11
      KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 0x10
      KeyEvent.KEYCODE_TAB -> 0x09
      else -> null
    }
  }
  private inner class GameRenderer : GLSurfaceView.Renderer {
    var gameW = 800
    var gameH = 600
    var contentRect = floatArrayOf(0f, 0f, 0f, 0f)
    private var program = 0
    private var texId = 0
    private var posHandle = 0
    private var texHandle = 0
    private var samplerHandle = 0
    private var serial: Long = -1
    private var frameBuf: ByteBuffer? = null
    private var surfaceW = 0
    private var surfaceH = 0
    private var stopped = false
    private var quadBuf: FloatBuffer? = null
    private var texBuf: FloatBuffer? = null
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
      GLES20.glClearColor(0f, 0f, 0f, 1f)
      program = createProgram()
      posHandle = GLES20.glGetAttribLocation(program, "aPos")
      texHandle = GLES20.glGetAttribLocation(program, "aTex")
      samplerHandle = GLES20.glGetUniformLocation(program, "uTex")
      val tex = IntArray(1)
      GLES20.glGenTextures(1, tex, 0)
      texId = tex[0]
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
      GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
      surfaceW = w
      surfaceH = h
      GLES20.glViewport(0, 0, w, h)
      updateFit()
    }
    override fun onDrawFrame(gl: GL10?) {
      if (stopped) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        return
      }
      val now = SystemClock.uptimeMillis()
      val r = engine.frame(now)
      if (r != 0) {
        stopped = true
        val err = if (r < 0) engine.lastError() else null
        mainHandler.post { onStop(err) }
        return
      }
      val size = IntArray(2)
      if (!engine.framebufferSize(size)) return
      if (size[0] != gameW || size[1] != gameH) {
        gameW = size[0]
        gameH = size[1]
        updateFit()
      }
      val s = engine.frameSerial()
      var needUpload = false
      if (s != serial) {
        serial = s
        needUpload = true
        if (frameBuf == null || frameBuf!!.capacity() < gameW * gameH * 4) {
          frameBuf = ByteBuffer.allocateDirect(gameW * gameH * 4).order(ByteOrder.nativeOrder())
        }
        frameBuf!!.rewind()
        engine.copyFramebuffer(frameBuf!!)
        frameBuf!!.rewind()
      }
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
      if (texId == 0) return
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
      if (needUpload) {
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, gameW, gameH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frameBuf)
      }
      GLES20.glUseProgram(program)
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
      GLES20.glUniform1i(samplerHandle, 0)
      val quad = quadBuf ?: return
      val tex = texBuf ?: return
      quad.position(0)
      tex.position(0)
      GLES20.glEnableVertexAttribArray(posHandle)
      GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, quad)
      GLES20.glEnableVertexAttribArray(texHandle)
      GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, tex)
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GLES20.glDisableVertexAttribArray(posHandle)
      GLES20.glDisableVertexAttribArray(texHandle)
    }
    private fun updateFit() {
      if (surfaceW <= 0 || surfaceH <= 0) return
      val scale = min(surfaceW.toFloat() / gameW, surfaceH.toFloat() / gameH)
      val w = gameW * scale
      val h = gameH * scale
      val x = (surfaceW - w) / 2f
      val y = (surfaceH - h) / 2f
      contentRect = floatArrayOf(x, y, w, h)
      val left = x / surfaceW * 2f - 1f
      val right = (x + w) / surfaceW * 2f - 1f
      val top = 1f - y / surfaceH * 2f
      val bottom = 1f - (y + h) / surfaceH * 2f
      val quad = floatArrayOf(left, bottom, right, bottom, left, top, right, top)
      val tex = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
      quadBuf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quad); position(0) }
      texBuf = ByteBuffer.allocateDirect(tex.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(tex); position(0) }
    }
    private fun createProgram(): Int {
      val vs = "attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex; void main() { gl_Position = vec4(aPos, 0.0, 1.0); vTex = aTex; }"
      val fs = "precision mediump float; varying vec2 vTex; uniform sampler2D uTex; void main() { vec4 c = texture2D(uTex, vTex); gl_FragColor = vec4(c.b, c.g, c.r, 1.0); }"
      val v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
      GLES20.glShaderSource(v, vs)
      GLES20.glCompileShader(v)
      val f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
      GLES20.glShaderSource(f, fs)
      GLES20.glCompileShader(f)
      val p = GLES20.glCreateProgram()
      GLES20.glAttachShader(p, v)
      GLES20.glAttachShader(p, f)
      GLES20.glLinkProgram(p)
      return p
    }
  }
}
