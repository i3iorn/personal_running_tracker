package dev.schrammel.personal_running_tracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import dev.schrammel.personal_running_tracker.data.GpsEntity

/**
 * History list rows: route thumbnail + run name + summary line.
 * Thumbnails are rendered off-thread by the owner and delivered via [setThumbnail].
 */
class HistoryAdapter(
    private val context: Context,
) : BaseAdapter() {

    data class Row(val runId: Long, val title: String, val subtitle: String)

    private val rows = mutableListOf<Row>()
    private val thumbnails = mutableMapOf<Long, Bitmap>()

    fun setRows(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    /** Called from the main thread after the bitmap was rendered off-thread. */
    fun setThumbnail(runId: Long, bitmap: Bitmap) {
        thumbnails[runId] = bitmap
        notifyDataSetChanged()
    }

    fun hasThumbnail(runId: Long): Boolean = thumbnails.containsKey(runId)

    override fun getCount(): Int = rows.size
    override fun getItem(position: Int): Row = rows[position]
    override fun getItemId(position: Int): Long = rows[position].runId

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_run, parent, false)
        val row = rows[position]
        view.findViewById<TextView>(R.id.rowTitle).text = row.title
        view.findViewById<TextView>(R.id.rowSubtitle).text = row.subtitle
        val image = view.findViewById<ImageView>(R.id.rowThumb)
        image.setImageBitmap(thumbnails[row.runId] ?: placeholder())
        return view
    }

    private fun placeholder(): Bitmap =
        Bitmap.createBitmap(52, 52, Bitmap.Config.ARGB_8888).also { bmp ->
            Canvas(bmp).drawColor(Color.argb(40, 128, 128, 128))
        }

    companion object {
        /** Renders a small route polyline thumbnail; call off the main thread. */
        fun renderThumbnail(points: List<GpsEntity>, sizePx: Int = 104): Bitmap {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.argb(30, 128, 128, 128))
            if (points.size < 2) return bmp
            val lats = points.map { it.latitude }
            val lngs = points.map { it.longitude }
            val minLat = lats.min(); val maxLat = lats.max()
            val minLng = lngs.min(); val maxLng = lngs.max()
            val spanLat = (maxLat - minLat).coerceAtLeast(1e-6)
            val spanLng = (maxLng - minLng).coerceAtLeast(1e-6)
            val pad = sizePx * 0.12f
            val usable = sizePx - 2 * pad
            fun x(lng: Double) = (pad + (lng - minLng) / spanLng * usable).toFloat()
            fun y(lat: Double) = (pad + (1 - (lat - minLat) / spanLat) * usable).toFloat()
            val path = Path()
            path.moveTo(x(points.first().longitude), y(points.first().latitude))
            for (p in points.drop(1)) path.lineTo(x(p.longitude), y(p.latitude))
            val paint = Paint().apply {
                color = Color.parseColor("#2E7D32")
                style = Paint.Style.STROKE
                strokeWidth = sizePx / 24f
                isAntiAlias = true
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(path, paint)
            return bmp
        }
    }
}
