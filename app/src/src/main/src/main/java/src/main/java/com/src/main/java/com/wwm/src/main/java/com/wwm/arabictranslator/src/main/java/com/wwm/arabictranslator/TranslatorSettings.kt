package com.wwm.arabictranslator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TranslateZone(
    val name: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val exclude: Boolean
)

class TranslatorSettings(context: Context) {
    private val p = context.getSharedPreferences("wwm_settings", Context.MODE_PRIVATE)

    var frameInterval: Long
        get() = p.getLong("frame_interval", 300L)
        set(v) = p.edit().putLong("frame_interval", v.coerceIn(120L, 1500L)).apply()

    var fontSize: Float
        get() = p.getFloat("font_size", 20f)
        set(v) = p.edit().putFloat("font_size", v.coerceIn(12f, 42f)).apply()

    var fontChoice: String
        get() = p.getString("font_choice", "sans") ?: "sans"
        set(v) = p.edit().putString("font_choice", v).apply()

    var textColor: Int
        get() = p.getInt("text_color", android.graphics.Color.WHITE)
        set(v) = p.edit().putInt("text_color", v).apply()

    var backgroundColor: Int
        get() = p.getInt("background_color", android.graphics.Color.argb(190, 0, 0, 0))
        set(v) = p.edit().putInt("background_color", v).apply()

    var overlayOpacity: Int
        get() = p.getInt("overlay_opacity", 80)
        set(v) = p.edit().putInt("overlay_opacity", v.coerceIn(20, 100)).apply()

    var showSource: Boolean
        get() = p.getBoolean("show_source", false)
        set(v) = p.edit().putBoolean("show_source", v).apply()

    var maxBlocks: Int
        get() = p.getInt("max_blocks", 6)
        set(v) = p.edit().putInt("max_blocks", v.coerceIn(1, 12)).apply()

    fun zones(): List<TranslateZone> {
        val raw = p.getString("zones", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    add(
                        TranslateZone(
                            o.optString("name", "منطقة"),
                            o.optDouble("left", 0.0).toFloat(),
                            o.optDouble("top", 0.0).toFloat(),
                            o.optDouble("right", 1.0).toFloat(),
                            o.optDouble("bottom", 1.0).toFloat(),
                            o.optBoolean("exclude", false)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveZone(zone: TranslateZone) {
        val a = JSONArray()
        zones().forEach {
            a.put(
                JSONObject()
                    .put("name", it.name)
                    .put("left", it.left)
                    .put("top", it.top)
                    .put("right", it.right)
                    .put("bottom", it.bottom)
                    .put("exclude", it.exclude)
            )
        }
        a.put(
            JSONObject()
                .put("name", zone.name)
                .put("left", zone.left)
                .put("top", zone.top)
                .put("right", zone.right)
                .put("bottom", zone.bottom)
                .put("exclude", zone.exclude)
        )
        p.edit().putString("zones", a.toString()).apply()
    }

    fun clearZones() = p.edit().remove("zones").apply()
}
