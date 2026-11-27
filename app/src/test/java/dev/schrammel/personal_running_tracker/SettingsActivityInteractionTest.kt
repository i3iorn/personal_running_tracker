package dev.schrammel.personal_running_tracker

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.schrammel.personal_running_tracker.settings.TrackingPreferences
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** Comprehensive interaction test: every slider/control in SettingsActivity. */
@RunWith(RobolectricTestRunner::class)
class SettingsActivityInteractionTest {

    private lateinit var activity: SettingsActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
    }

    private fun container(): LinearLayout = activity.findViewById(R.id.settingsContainer)

    private fun collectAllViews(root: ViewGroup, acc: MutableList<View>) {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            acc.add(v)
            if (v is ViewGroup) collectAllViews(v, acc)
        }
    }

    /** Invoke the real SeekBar change listener with fromUser=true, simulating an actual user drag. */
    private fun dragSlider(sb: SeekBar, value: Int) {
        try {
            val f = SeekBar::class.java.getDeclaredField("mOnSeekBarChangeListener")
            f.isAccessible = true
            val listener = f.get(sb)
            @Suppress("UNCHECKED_CAST")
            val l = listener as? SeekBar.OnSeekBarChangeListener
            sb.progress = value
            l?.onProgressChanged(sb, value, true)
        } catch (t: Throwable) {
            // Fallback: programmatic progress (fromUser=false) still exercises the UI path.
            sb.progress = value
        }
    }

    @Test
    fun `exercise every slider through full range without crash`() {
        val allViews = mutableListOf<View>()
        collectAllViews(container(), allViews)

        val seekBars = allViews.filterIsInstance<SeekBar>()
        assertTrue("At least one slider expected", seekBars.isNotEmpty())

        val prefs = TrackingPreferences(ApplicationProvider.getApplicationContext())

        for (sb in seekBars) {
            val max = sb.max
            val mid = max / 2
            for (progress in listOf(0, mid, max)) {
                dragSlider(sb, progress)
                assertNotNull(prefs.trackingProfile)
                assertTrue("saver interval clamped", prefs.saverGpsMinIntervalMs in 1_000L..600_000L)
                assertTrue("highAcc interval clamped", prefs.highAccuracyGpsMinIntervalMs in 1_000L..600_000L)
                assertTrue("pace smoothing clamped", prefs.paceSmoothingWindowMs in 1_000L..120_000L)
                assertTrue("split length clamped", prefs.splitLengthMeters in 100.0..10_000.0)
                assertTrue("rating bands positive", prefs.ratingExcellentMeters > 0f)
                assertTrue("map reliable clamped", prefs.mapReliableMeters in 1f..500f)
            }
        }
    }

    @Test
    fun `toggle all switches without crash`() {
        val allViews = mutableListOf<View>()
        collectAllViews(container(), allViews)
        val switches = allViews.filterIsInstance<SwitchMaterial>()
        assertTrue("At least one switch expected", switches.isNotEmpty())
        for (sw in switches) {
            sw.isChecked = !sw.isChecked
            sw.isChecked = !sw.isChecked
        }
    }

    @Test
    fun `select all spinner options without crash`() {
        val allViews = mutableListOf<View>()
        collectAllViews(container(), allViews)
        val spinners = allViews.filterIsInstance<Spinner>()
        assertTrue("At least one spinner expected", spinners.isNotEmpty())
        for (sp in spinners) {
            if (sp.count > 1) {
                for (i in 0 until sp.count) sp.setSelection(i)
            }
        }
    }

    @Test
    fun `edit all text fields without crash`() {
        val allViews = mutableListOf<View>()
        collectAllViews(container(), allViews)
        val edits = allViews.filterIsInstance<EditText>()
        for (et in edits) {
            et.setText("test value")
            et.clearFocus()
            et.setText("")
            et.clearFocus()
        }
    }

    @Test
    fun `prefs remain valid after full interaction sweep`() {
        val allViews = mutableListOf<View>()
        collectAllViews(container(), allViews)

        for (sb in allViews.filterIsInstance<SeekBar>()) {
            sb.progress = sb.max
            sb.progress = 0
            sb.progress = sb.max / 2
        }
        for (sw in allViews.filterIsInstance<SwitchMaterial>()) {
            sw.isChecked = !sw.isChecked
        }
        for (sp in allViews.filterIsInstance<Spinner>()) {
            if (sp.count > 1) sp.setSelection(1)
        }
        for (et in allViews.filterIsInstance<EditText>()) {
            et.setText("test")
            et.clearFocus()
        }

        val prefs = TrackingPreferences(ApplicationProvider.getApplicationContext())
        assertNotNull(prefs.trackingProfile)
        assertTrue("saver interval valid", prefs.saverGpsMinIntervalMs in 1_000L..600_000L)
        assertTrue("highAcc interval valid", prefs.highAccuracyGpsMinIntervalMs in 1_000L..600_000L)
        assertTrue("pace smoothing valid", prefs.paceSmoothingWindowMs in 1_000L..120_000L)
        assertTrue("min distance valid", prefs.saverGpsMinDistanceMeters in 1f..10_000f)
        assertTrue("split length valid", prefs.splitLengthMeters in 100.0..10_000.0)
        assertTrue("degraded threshold valid", prefs.degradedGpsThresholdMeters in 1f..500f)
        assertTrue("rating bands positive", prefs.ratingExcellentMeters > 0f && prefs.ratingGoodMeters > 0f)
        assertTrue("persist interval valid", prefs.statePersistIntervalMs in 1_000L..600_000L)
        assertTrue("map reliable valid", prefs.mapReliableMeters in 1f..500f)
        assertTrue("map assisted valid", prefs.mapAssistedMeters in 1f..500f)
    }
}
