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

    private val dbFile: File = File(context.filesDir.parentFile, "databases/${context.packageName}.db")
    private val backupDir: File = File(context.filesDir.parentFile, "backups")

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
            zipOut.putNextEntry(ZipEntry("database.db"))
            val dbIn = FileInputStream(dbFile)
            dbIn.copyTo(zipOut)
            dbIn.close()
            zipOut.closeEntry()

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
            val zipIn = ZipInputStream(FileInputStream(backupFile))
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == "database.db") {
                    val dbOut = FileOutputStream(dbFile)
                    zipIn.copyTo(dbOut)
                    dbOut.close()
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

    private fun createManifest(): String {
        return """{
    |  "app": "Personal Running Tracker",
    |  "version": "1.0",
    |  "timestamp": ${System.currentTimeMillis()},
    |  "databaseVersion": 1
    |}""".trimMargin()
    }
}