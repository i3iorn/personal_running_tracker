package dev.schrammel.personal_running_tracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

class BackupManager(private val context: Context) {

    private val dbFile: File = context.getDatabasePath("personal_running_tracker.db")
    private val backupDir: File = File(context.getExternalFilesDir(null), "export/backups")

    init {
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
    }

    fun createBackup(): File {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val backupFile = File(backupDir, "backup_${timestamp}.zip")

        val zipOut = ZipOutputStream(FileOutputStream(backupFile))
        try {
            // Room keeps -wal/-shm sidecars; back them up too or recent writes may be lost.
            for (suffix in listOf("", "-wal", "-shm")) {
                val f = File(dbFile.parentFile, dbFile.name + suffix)
                if (f.exists()) {
                    zipOut.putNextEntry(ZipEntry(dbFile.name + suffix))
                    val dbIn = FileInputStream(f)
                    dbIn.copyTo(zipOut)
                    dbIn.close()
                    zipOut.closeEntry()
                }
            }
            // Shared preferences (settings + tuning knobs)
            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            prefsDir.listFiles()?.forEach { prefs ->
                zipOut.putNextEntry(ZipEntry("prefs/${prefs.name}"))
                FileInputStream(prefs).use { it.copyTo(zipOut) }
                zipOut.closeEntry()
            }

            zipOut.putNextEntry(ZipEntry("manifest.json"))
            val manifest = createManifest()
            zipOut.write(manifest.toByteArray())
            zipOut.closeEntry()
        } finally {
            zipOut.close()
        }

        return backupFile
    }

    fun restoreBackup(backupFile: File): Boolean {
        if (!backupFile.exists()) return false

        try {
            // Close the Room singleton so the files can be replaced safely.
            AppDatabase.closeInstance()
            val zipIn = ZipInputStream(FileInputStream(backupFile))
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == dbFile.name || entry.name.startsWith(dbFile.name + "-")) {
                    val target = safeTarget(File(dbFile.parentFile, entry.name), dbFile.parentFile)
                        ?: throw SecurityException("Blocked path traversal in backup: ${entry.name}")
                    val dbOut = FileOutputStream(target)
                    zipIn.copyTo(dbOut)
                    dbOut.close()
                } else if (entry.name.startsWith("prefs/")) {
                    val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                    prefsDir.mkdirs()
                    val target = safeTarget(
                        File(prefsDir, entry.name.removePrefix("prefs/")), prefsDir)
                        ?: throw SecurityException("Blocked path traversal in backup: ${entry.name}")
                    val out = FileOutputStream(target)
                    zipIn.copyTo(out)
                    out.close()
                }
                entry = zipIn.nextEntry
            }
            zipIn.close()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun listBackups(): List<File> {
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()?.filter { it.name.endsWith(".zip") }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    /** Zip-slip guard: the resolved target must stay inside the base directory. */
    private fun safeTarget(candidate: File, baseDir: File): File? {
        val canonicalBase = baseDir.canonicalFile
        val canonical = candidate.canonicalFile
        return if (canonical.path.startsWith(canonicalBase.path + File.separator) ||
            canonical.path == canonicalBase.path
        ) canonical else null
    }

    private fun createManifest(): String {
        return """{
    |  "app": "Personal Running Tracker",
    |  "version": "1.0",
    |  "timestamp": ${System.currentTimeMillis()},
    |  "databaseVersion": 1
    |}""".trimMargin()
    }
}