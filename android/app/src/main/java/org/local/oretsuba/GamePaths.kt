package org.local.oretsuba

import android.content.Context
import java.io.File

object GamePaths {
  fun roots(context: Context): List<File> {
    val list = ArrayList<File>()
    list.add(context.filesDir)
    val ext = context.getExternalFilesDir(null)
    if (ext != null) list.add(ext)
    return list
  }
  fun savesDir(context: Context, language: String): File {
    val d = File(context.filesDir, "Saves/" + language)
    d.mkdirs()
    return d
  }
  fun findGameFolder(context: Context): File? {
    for (root in roots(context)) {
      val found = search(root, 3) { f -> hasFile(f, "SCRIPT.LPK") }
      if (found != null) return found
    }
    return null
  }
  fun findPatchFolder(context: Context): File? {
    for (root in roots(context)) {
      val found = search(root, 3) { f -> hasDir(f, "SCRIPT_CHS") }
      if (found != null) return found
    }
    return null
  }
  private fun search(dir: File, depth: Int, match: (File) -> Boolean): File? {
    if (!dir.isDirectory) return null
    if (match(dir)) return dir
    if (depth <= 0) return null
    val items = dir.listFiles() ?: return null
    items.sortBy { it.name }
    for (item in items) {
      if (item.isDirectory) {
        val found = search(item, depth - 1, match)
        if (found != null) return found
      }
    }
    return null
  }
  private fun hasFile(dir: File, name: String): Boolean {
    val items = dir.listFiles() ?: return false
    for (it in items) {
      if (!it.isDirectory && it.name.uppercase() == name) return true
    }
    return false
  }
  private fun hasDir(dir: File, name: String): Boolean {
    val items = dir.listFiles() ?: return false
    for (it in items) {
      if (it.isDirectory && it.name.uppercase() == name) return true
    }
    return false
  }
}
