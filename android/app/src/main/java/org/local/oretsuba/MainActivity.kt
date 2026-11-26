package org.local.oretsuba

import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity(), Engine.Listener {
  private lateinit var root: FrameLayout
  private var engine: Engine? = null
  private var helper: PlatformHelper? = null
  private var audio: AudioPlayer? = null
  private var gameView: GameView? = null
  private var playing = false
  private var gameFolder: File? = null
  private var patchFolder: File? = null
  private var languageChinese = true
  private val mainHandler = Handler(Looper.getMainLooper())
  private var importProgress: TextView? = null
  private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
      importProgress?.text = "Copying..."
      FileImport.importTree(this, uri, { name ->
        mainHandler.post { importProgress?.text = "Copying " + name }
      }, { result ->
        mainHandler.post {
          importProgress?.text = ""
          result.onSuccess {
            Toast.makeText(this, "Imported " + it.name, Toast.LENGTH_SHORT).show()
            refresh()
            showLauncher(null)
          }
          result.onFailure {
            Toast.makeText(this, "Import failed: " + it.message, Toast.LENGTH_LONG).show()
          }
        }
      })
    }
  }
  private fun hideSystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false)
      window.insetsController?.let {
        it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      @Suppress("DEPRECATION")
      window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        or View.SYSTEM_UI_FLAG_FULLSCREEN
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      )
    }
  }
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) hideSystemUI()
  }
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    languageChinese = getSharedPreferences("oretsuba", MODE_PRIVATE).getBoolean("chinese", true)
    root = FrameLayout(this)
    root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    setContentView(root)
    hideSystemUI()
    refresh()
    showLauncher(null)
  }
  private fun refresh() {
    gameFolder = GamePaths.findGameFolder(this)
    patchFolder = GamePaths.findPatchFolder(this)
  }
  private fun makeText(text: String, size: Float, bold: Boolean, color: Int): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = size
    t.setTextColor(color)
    if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD)
    return t
  }
  private fun makeButton(text: String, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = text
    b.setOnClickListener { onClick() }
    return b
  }
  private fun showLauncher(error: String?) {
    playing = false
    hideSystemUI()
    root.removeAllViews()
    val scroll = ScrollView(this)
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(48, 48, 48, 48)
    col.gravity = Gravity.CENTER_HORIZONTAL
    val title = makeText("We Have No Wings", 26f, true, Color.WHITE)
    title.gravity = Gravity.CENTER
    col.addView(title)
    val sub = makeText("Oretsuba engine - Android", 14f, false, Color.LTGRAY)
    sub.gravity = Gravity.CENTER
    col.addView(sub)
    if (error != null) {
      val e = makeText(error, 14f, false, Color.rgb(255, 120, 120))
      col.addView(e)
    }
    val gameLine = if (gameFolder != null) "Game: found - " + gameFolder!!.name else "Game: not found - import SET_UP folder"
    val patchLine = if (patchFolder != null) "Patch: found - " + patchFolder!!.name else "Patch: not found (Chinese will fall back to Japanese)"
    val status = makeText(gameLine + "\n" + patchLine, 14f, false, Color.WHITE)
    col.addView(status)
    val langLabel = makeText("Language", 16f, true, Color.WHITE)
    col.addView(langLabel)
    val rg = RadioGroup(this)
    val rbCh = RadioButton(this)
    rbCh.id = View.generateViewId()
    rbCh.text = "Chinese (needs SCRIPT_CHS patch)"
    val rbJp = RadioButton(this)
    rbJp.id = View.generateViewId()
    rbJp.text = "Japanese"
    rg.addView(rbCh)
    rg.addView(rbJp)
    rg.check(if (languageChinese) rbCh.id else rbJp.id)
    rg.setOnCheckedChangeListener { _, checkedId ->
      languageChinese = (checkedId == rbCh.id)
      getSharedPreferences("oretsuba", MODE_PRIVATE).edit().putBoolean("chinese", languageChinese).apply()
    }
    col.addView(rg)
    val startBtn = makeButton("Start Game") { startGame() }
    startBtn.isEnabled = gameFolder != null
    col.addView(startBtn)
    val importBtn = makeButton("Import SET_UP / patch folder") {
      importLauncher.launch(null)
    }
    col.addView(importBtn)
    val refreshBtn = makeButton("Refresh") {
      refresh()
      showLauncher(null)
    }
    col.addView(refreshBtn)
    val prog = TextView(this)
    prog.setTextColor(Color.LTGRAY)
    importProgress = prog
    col.addView(prog)
    val help = makeText("Controls: Tap = left click, Two-finger tap = right click, Swipe = wheel / backlog, Long-press = skip (Ctrl).\nGame files are NOT in the APK. Import them after install. Saves go to internal storage.", 13f, false, Color.LTGRAY)
    col.addView(help)
    scroll.addView(col)
    root.addView(scroll)
  }
  private fun startGame() {
    val dataDir = gameFolder?.absolutePath ?: return
    val effectiveChinese = languageChinese && patchFolder != null
    val patchDir = if (effectiveChinese) patchFolder?.absolutePath else null
    val lang = if (effectiveChinese) "chs" else "jpn"
    val saveDir = GamePaths.savesDir(this, lang).absolutePath
    val h = PlatformHelper()
    val eng = Engine()
    eng.listener = this
    if (!eng.create(dataDir, patchDir, saveDir, effectiveChinese, h)) {
      showLauncher(eng.createError)
      return
    }
    helper = h
    engine = eng
    val gv = GameView(this, eng) { err ->
      onStopped(err)
    }
    gameView = gv
    root.removeAllViews()
    val container = FrameLayout(this)
    container.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    gv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    container.addView(gv)
    // Right-edge collapsible panel, hidden by default. Only the
    // small white "<" toggle is visible at the right edge.
    val panel = LinearLayout(this)
    panel.orientation = LinearLayout.VERTICAL
    panel.setBackgroundColor(Color.argb(180, 0, 0, 0))
    panel.gravity = Gravity.CENTER
    panel.visibility = View.GONE
    val cmds = listOf(Pair("Save", 3), Pair("Load", 4), Pair("Q.Save", 0), Pair("Q.Load", 1), Pair("History", 7), Pair("Menu", 8))
    for (c in cmds) {
      val b = Button(this)
      b.text = c.first
      b.textSize = 11f
      b.minWidth = 0
      b.minimumWidth = 0
      b.setPadding(24, 8, 24, 8)
      b.setOnClickListener { gv.sendCommand(c.second) }
      panel.addView(b)
    }
    val toggle = Button(this)
    toggle.text = "<"
    toggle.textSize = 14f
    toggle.setTextColor(Color.WHITE)
    toggle.setBackgroundColor(Color.argb(120, 0, 0, 0))
    toggle.minWidth = 0
    toggle.minimumWidth = 0
    toggle.setPadding(12, 24, 12, 24)
    toggle.setOnClickListener {
      if (panel.visibility == View.VISIBLE) {
        panel.visibility = View.GONE
        toggle.text = "<"
      } else {
        panel.visibility = View.VISIBLE
        toggle.text = ">"
      }
    }
    val side = LinearLayout(this)
    side.orientation = LinearLayout.HORIZONTAL
    side.gravity = Gravity.CENTER_VERTICAL
    side.addView(panel)
    side.addView(toggle)
    val sideParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
    sideParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
    container.addView(side, sideParams)
    hideSystemUI()
    root.addView(container)
    playing = true
    val a = AudioPlayer(eng)
    audio = a
    a.start()
    gv.requestFocus()
  }
  private fun stopToLauncher(error: String?) {
    playing = false
    try {
      audio?.stop()
    } catch (e: Exception) {
    }
    audio = null
    try {
      engine?.destroy()
    } catch (e: Exception) {
    }
    engine = null
    gameView = null
    refresh()
    showLauncher(error)
  }
  override fun onDialogRequested(text: String, kind: Int) {
    val b = AlertDialog.Builder(this).setMessage(text).setCancelable(false)
    if (kind == 1) {
      b.setPositiveButton("Yes") { _, _ -> engine?.answerDialog(1) }
      b.setNegativeButton("No") { _, _ -> engine?.answerDialog(0) }
    } else {
      b.setPositiveButton("OK") { _, _ -> engine?.answerDialog(1) }
    }
    b.show()
  }
  override fun onTextInputRequested(initial: String) {
    val edit = EditText(this)
    edit.setText(initial)
    AlertDialog.Builder(this).setTitle("Save comment").setView(edit).setCancelable(false)
      .setPositiveButton("OK") { _, _ -> engine?.answerTextInput(true, edit.text.toString()) }
      .setNegativeButton("Cancel") { _, _ -> engine?.answerTextInput(false, null) }
      .show()
  }
  override fun onStopped(error: String?) {
    mainHandler.post { stopToLauncher(error) }
  }
  override fun onPause() {
    super.onPause()
    if (playing) {
      engine?.setPaused(true)
      audio?.pause()
      gameView?.onPause()
    }
  }
  override fun onResume() {
    super.onResume()
    hideSystemUI()
    if (playing) {
      gameView?.onResume()
      engine?.setPaused(false)
      audio?.resume()
    }
  }
  override fun onDestroy() {
    super.onDestroy()
    try {
      audio?.stop()
    } catch (e: Exception) {
    }
    try {
      engine?.destroy()
    } catch (e: Exception) {
    }
  }
  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    if (playing) {
      AlertDialog.Builder(this).setMessage("Exit to launcher?").setPositiveButton("Exit") { _, _ -> stopToLauncher(null) }.setNegativeButton("Stay", null).show()
    } else {
      super.onBackPressed()
    }
  }
}
