package dev.schrammel.personal_running_tracker.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Backup/restore round trip (spec §12.3 data-integrity tests). */
@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun createBackup_writesZipContainingManifestAndDatabase() {
        // Seed a database file so the backup has one to include
        val dbFile = context.getDatabasePath("personal_running_tracker.db")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("DB")
        val backup = BackupManager(context).createBackup()
        println("DBG backup=${backup.absolutePath} exists=${backup.exists()} len=${backup.length()}")
        assertTrue(backup.exists() && backup.length() > 0)
        val names = mutableListOf<String>()
        java.util.zip.ZipInputStream(backup.inputStream().buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        println("DBG names=$names")
        assertTrue(names.contains("manifest.json"))
        assertTrue(names.contains("personal_running_tracker.db"))
    }

    @Test
    fun restoreBackup_replacesDatabaseAndPrefs() {
        val dbFile = context.getDatabasePath("personal_running_tracker.db")
        dbFile.parentFile?.mkdirs()
        // A pre-existing "database" with known content that restore must overwrite
        dbFile.writeText("OLD_DB_CONTENT")

        val zip = File(context.cacheDir, "manual-backup.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("personal_running_tracker.db"))
            out.write("NEW_DB_CONTENT".toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("prefs/settings.xml"))
            out.write("<map><boolean name=\"x\" value=\"true\"/></map>".toByteArray())
            out.closeEntry()
        }

        val ok = BackupManager(context).restoreBackup(zip)
        assertTrue(ok)
        assertEquals("NEW_DB_CONTENT", dbFile.readText())
        val prefsFile = File(File(context.applicationInfo.dataDir, "shared_prefs"), "settings.xml")
        assertEquals("<map><boolean name=\"x\" value=\"true\"/></map>", prefsFile.readText())
    }

    @Test
    fun restoreBackup_failsGracefullyOnMissingFile() {
        assertEquals(false, BackupManager(context).restoreBackup(File(context.cacheDir, "nope.zip")))
    }

    @Test
    fun restoreBackup_blocksZipSlipPathTraversal() {
        // A malicious backup trying to write outside shared_prefs via ../ must be refused
        val zip = File(context.cacheDir, "evil-backup.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("prefs/../../evil.xml"))
            out.write("<pwn/>".toByteArray())
            out.closeEntry()
        }
        val ok = BackupManager(context).restoreBackup(zip)
        assertEquals(false, ok)
        val escaped = File(context.applicationInfo.dataDir, "evil.xml")
        assertEquals(false, escaped.exists())
    }
}
