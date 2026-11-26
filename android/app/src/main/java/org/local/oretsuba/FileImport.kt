package org.local.oretsuba

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

object FileImport {
  fun importTree(context: Context, treeUri: Uri, onProgress: (String) -> Unit, onDone: (Result<File>) -> Unit) {
    Thread {
      try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
          context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
        }
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDoc == null || !rootDoc.exists()) throw IllegalStateException("Picked folder not found")
        val name = rootDoc.name ?: "SET_UP"
        val destRoot = context.filesDir
        val finalDir = File(destRoot, name)
        if (finalDir.exists()) throw IllegalStateException("A folder named " + name + " already exists. Rename it in Files first, then import again.")
        val staging = File(destRoot, ".import-" + UUID.randomUUID().toString())
        staging.mkdirs()
        try {
          copyDoc(context, rootDoc, staging, onProgress)
          if (!staging.renameTo(finalDir)) {
            copyFileDir(staging, finalDir)
            deleteRecursive(staging)
          }
        } catch (e: Exception) {
          deleteRecursive(staging)
          throw e
        }
        onDone(Result.success(finalDir))
      } catch (e: Exception) {
        onDone(Result.failure(e))
      }
    }.start()
  }
  private fun copyDoc(context: Context, doc: DocumentFile, targetDir: File, onProgress: (String) -> Unit) {
    val children = doc.listFiles()
    for (child in children) {
      val childName: String = child.name ?: continue
      if (childName.isNotEmpty() && childName[0] == '.') continue
      if (child.isDirectory) {
        val sub = File(targetDir, childName)
        sub.mkdirs()
        copyDoc(context, child, sub, onProgress)
      } else {
        val outFile = File(targetDir, childName)
        onProgress(childName)
        context.contentResolver.openInputStream(child.uri)?.use { input ->
          FileOutputStream(outFile).use { output ->
            val buf = ByteArray(256 * 1024)
            while (true) {
              val n = input.read(buf)
              if (n <= 0) break
              output.write(buf, 0, n)
            }
          }
        } ?: throw IllegalStateException("Cannot read " + childName)
      }
    }
  }
  private fun copyFileDir(src: File, dst: File) {
    if (src.isDirectory) {
      dst.mkdirs()
      val items = src.listFiles() ?: return
      for (it in items) {
        copyFileDir(it, File(dst, it.name))
      }
    } else {
      dst.parentFile?.mkdirs()
      FileInputStream(src).use { input ->
        FileOutputStream(dst).use { output ->
          val buf = ByteArray(256 * 1024)
          while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
          }
        }
      }
    }
  }
  private fun deleteRecursive(f: File) {
    if (f.isDirectory) {
      f.listFiles()?.forEach { deleteRecursive(it) }
    }
    f.delete()
  }
}
