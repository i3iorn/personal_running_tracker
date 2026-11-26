package dev.schrammel.personal_running_tracker.data

import androidx.room.TypeConverter
import java.util.Locale

@JvmSuppressWildcards
class Converters {

    @TypeConverter
    fun floatArrayToString(values: FloatArray): String {
        if (values.isEmpty()) return ""
        val sb = StringBuilder()
        for (i in values.indices) {
            if (i > 0) sb.append(",")
            sb.append(values[i].toString())
        }
        return sb.toString()
    }

    @TypeConverter
    fun stringToFloatArray(value: String?): FloatArray {
        if (value.isNullOrEmpty()) return FloatArray(0)
        val parts = value.split(",")
        val array = FloatArray(parts.size) { i -> parts[i].toFloatOrNull() ?: 0f }
        return array
    }
}
