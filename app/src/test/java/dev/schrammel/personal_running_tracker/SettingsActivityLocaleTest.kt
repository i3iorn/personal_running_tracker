package dev.schrammel.personal_running_tracker

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * Reproduces the device crash: dragging a slider calls snap(), which formats with the
 * default locale and parses back with toDouble(). On a comma-decimal locale (de-DE,
 * fr-FR, ...) "%.1f".format(2.2) yields "2,2" and toDouble() throws NumberFormatException.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsActivityLocaleTest {

    private fun collectAllViews(root: ViewGroup, acc: MutableList<View>) {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            acc.add(v)
            if (v is ViewGroup) collectAllViews(v, acc)
        }
    }

    @Test
    fun `slider drag works under comma decimal locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY) // decimal comma
            val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
            val container = activity.findViewById<LinearLayout>(R.id.settingsContainer)
            val allViews = mutableListOf<View>()
            collectAllViews(container, allViews)
            val seekBars = allViews.filterIsInstance<SeekBar>()
            val f = SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener")
            f.isAccessible = true
            for (sb in seekBars) {
                val listener = f.get(sb) as SeekBar.OnSeekBarChangeListener
                // Simulate a real user drag (fromUser = true) across the range.
                for (p in listOf(0, sb.max / 2, sb.max)) {
                    listener.onProgressChanged(sb, p, true)
                }
            }
        } catch (e: NumberFormatException) {
            fail("Slider drag crashed under comma-decimal locale: ${e.message}")
        } finally {
            Locale.setDefault(previous)
        }
    }
}
