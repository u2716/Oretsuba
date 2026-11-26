package org.local.oretsuba

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

class PlatformHelper {
  private val sjis: Charset by lazy { Charset.forName("Shift_JIS") }
  private val gbk: Charset by lazy { Charset.forName("GBK") }
  private fun charset(gbk: Boolean): Charset {
    return if (gbk) this.gbk else sjis
  }
  fun decodeChar(code: Int, gbk: Boolean): Int {
    val bytes: ByteArray = if (code > 0xFF) {
      byteArrayOf(((code shr 8) and 0xFF).toByte(), (code and 0xFF).toByte())
    } else {
      byteArrayOf((code and 0xFF).toByte())
    }
    val cs = charset(gbk)
    val decoder = cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    val cb = try {
      decoder.decode(ByteBuffer.wrap(bytes))
    } catch (e: Exception) {
      return 63
    }
    if (cb.length == 0) return 63
    val c0 = cb.get(0)
    if (Character.isHighSurrogate(c0) && cb.length >= 2) {
      return Character.toCodePoint(c0, cb.get(1))
    }
    return c0.code
  }
  fun toUtf8(bytes: ByteArray, gbk: Boolean): ByteArray {
    val cs = charset(gbk)
    val decoder = cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    val str = try {
      decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: Exception) {
      return bytes
    }
    return str.toByteArray(Charsets.UTF_8)
  }
  fun fromUtf8(utf8: String, gbk: Boolean): ByteArray {
    val cs = charset(gbk)
    val encoder = cs.newEncoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE)
    val bb = try {
      encoder.encode(CharBuffer.wrap(utf8))
    } catch (e: Exception) {
      return ByteArray(0)
    }
    val out = ByteArray(bb.remaining())
    bb.get(out)
    return out
  }
  fun renderGlyph(codepoint: Int, serif: Boolean, bold: Boolean, size: Int, chinese: Boolean): ByteArray? {
    if (codepoint <= 0) return null
    val str: String = try {
      String(Character.toChars(codepoint))
    } catch (e: Exception) {
      return null
    }
    val base: Typeface = if (serif) Typeface.SERIF else Typeface.SANS_SERIF
    val face: Typeface = if (bold) Typeface.create(base, Typeface.BOLD) else base
    val paint = Paint()
    paint.typeface = face
    paint.textSize = size.toFloat()
    paint.isAntiAlias = true
    paint.style = Paint.Style.FILL
    val advance = Math.round(paint.measureText(str))
    if (str.trim().isEmpty()) {
      val buf = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
      buf.putInt(0)
      buf.putInt(0)
      buf.putInt(0)
      buf.putInt(0)
      buf.putInt(advance)
      return buf.array()
    }
    val bounds = Rect()
    paint.getTextBounds(str, 0, str.length, bounds)
    if (bounds.width() <= 0 || bounds.height() <= 0) return null
    val left = bounds.left - 1
    val top = 1 - bounds.top
    val w = bounds.width() + 2
    val h = bounds.height() + 2
    if (w <= 0 || h <= 0 || w > 512 || h > 512) return null
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawText(str, (-left).toFloat(), top.toFloat(), paint)
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    bmp.recycle()
    val alpha = ByteArray(w * h)
    for (i in pixels.indices) {
      alpha[i] = ((pixels[i] ushr 24) and 0xFF).toByte()
    }
    val buf = ByteBuffer.allocate(20 + alpha.size).order(ByteOrder.LITTLE_ENDIAN)
    buf.putInt(w)
    buf.putInt(h)
    buf.putInt(left)
    buf.putInt(top)
    buf.putInt(advance)
    buf.put(alpha)
    return buf.array()
  }
}
