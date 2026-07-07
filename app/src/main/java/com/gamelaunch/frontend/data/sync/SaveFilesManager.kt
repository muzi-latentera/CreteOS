package com.gamelaunch.frontend.data.sync

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A Syncthing conflict copy that the user needs to resolve. */
data class ConflictFile(val path: String, val name: String, val folderLabel: String)

/**
 * Local file-side of Save Sync: a one-time safety backup of save folders before the first sync, and
 * discovery/resolution of Syncthing's `.sync-conflict-*` copies (so diverged saves are never lost
 * silently).
 */
@Singleton
class SaveFilesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val backupsDir: File get() = File(context.filesDir, "sync_backups").apply { mkdirs() }
    private val marker: File get() = File(backupsDir, ".initial_done")

    fun hasInitialBackup(): Boolean = marker.exists()

    /** Zip each non-empty folder into filesDir/sync_backups; returns how many were backed up. */
    fun backup(folders: List<SyncthingController.SyncFolder>): Int {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var count = 0
        folders.forEach { folder ->
            val src = File(folder.path)
            val hasFiles = src.isDirectory && (src.listFiles()?.any { it.isFile } == true)
            if (!hasFiles) return@forEach
            val dest = File(backupsDir, "${folder.id}-$ts.zip")
            runCatching { zipDirectory(src, dest) }
                .onSuccess { count++ }
                .onFailure { Log.w(TAG, "Backup of ${folder.path} failed: ${it.message}") }
        }
        runCatching { marker.createNewFile() }
        return count
    }

    /** Find Syncthing conflict copies across the given folder paths. */
    fun findConflicts(folders: List<SyncthingController.SyncFolder>): List<ConflictFile> =
        folders.flatMap { folder ->
            val root = File(folder.path)
            if (!root.isDirectory) emptyList()
            else root.walkTopDown()
                .filter { it.isFile && it.name.contains(CONFLICT_MARKER) }
                .map { ConflictFile(it.absolutePath, it.name, folder.label) }
                .toList()
        }

    /**
     * Resolve a conflict: [keep] = promote this copy over the base file (strip the conflict marker);
     * otherwise just delete the conflict copy.
     */
    fun resolveConflict(path: String, keep: Boolean): Boolean = runCatching {
        val file = File(path)
        if (!file.exists()) return false
        if (keep) {
            val original = File(file.parentFile, CONFLICT_RE.replace(file.name, ""))
            if (original.exists()) original.delete()
            file.renameTo(original)
        } else {
            file.delete()
        }
    }.getOrElse { Log.w(TAG, "resolveConflict failed: ${it.message}"); false }

    private fun zipDirectory(src: File, dest: File) {
        ZipOutputStream(dest.outputStream().buffered()).use { zip ->
            src.walkTopDown().filter { it.isFile }.forEach { file ->
                val entry = ZipEntry(file.relativeTo(src).path)
                zip.putNextEntry(entry)
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    companion object {
        private const val TAG = "SaveFilesManager"
        private const val CONFLICT_MARKER = ".sync-conflict-"
        // e.g. name.sync-conflict-20240101-120000-ABCDEFG.ext  ->  removes ".sync-conflict-…-…-…"
        private val CONFLICT_RE = Regex("\\.sync-conflict-\\d{8}-\\d{6}-[A-Z0-9]+")
    }
}
