package com.wwm.arabictranslator

import android.content.Context
import android.graphics.Color

class TranslatorSettings(context: Context) {
    private val p = context.getSharedPreferences("wwm_settings", Context.MODE_PRIVATE)
    var fontSize: Float get() = p.getFloat("fontSize", 20f); set(v) = p.edit().putFloat("fontSize", v.coerceIn(12f, 42f)).apply()
    var fontChoice: String get() = p.getString("fontChoice", "sans") ?: "sans"; set(v) = p.edit().putString("fontChoice", v).apply()
    var textColor: Int get() = p.getInt("textColor", Color.WHITE); set(v) = p.edit().putInt("textColor", v).apply()
    var backgroundColor: Int get() = p.getInt("backgroundColor", Color.argb(190, 0, 0, 0)); set(v) = p.edit().putInt("backgroundColor", v).apply()
    var frameInterval: Long get() = p.getLong("frameInterval", 250L); set(v) = p.edit().putLong("frameInterval", v.coerceIn(120L, 1500L)).apply()
    var maxBlocks: Int get() = p.getInt("maxBlocks", 6); set(v) = p.edit().putInt("maxBlocks", v.coerceIn(1, 12)).apply()
    var enabled: Boolean get() = p.getBoolean("enabled", true); set(v) = p.edit().putBoolean("enabled", v).apply()
    var showSource: Boolean get() = p.getBoolean("showSource", false); set(v) = p.edit().putBoolean("showSource", v).apply()
    var overlayOpacity: Int get() = p.getInt("overlayOpacity", 100); set(v) = p.edit().putInt("overlayOpacity", v.coerceIn(20, 100)).apply()
    var bubblePadding: Int get() = p.getInt("bubblePadding", 10); set(v) = p.edit().putInt("bubblePadding", v.coerceIn(0, 40)).apply()
    var lineSpacing: Float get() = p.getFloat("lineSpacing", 1.0f); set(v) = p.edit().putFloat("lineSpacing", v.coerceIn(0.8f, 1.8f)).apply()

    data class Zone(val left: Float, val top: Float, val right: Float, val bottom: Float, val name: String = "منطقة", val exclude: Boolean = true)
    fun zones(): MutableList<Zone> = mutableListOf<Zone>().also { list ->
        val raw = p.getStringSet("zones", emptySet()) ?: emptySet()
        raw.forEach { s ->
            val parts=s.split('|'); val a=parts.getOrNull(0)?.split(',')?.mapNotNull{it.toFloatOrNull()} ?: emptyList()
            if(a.size==4){ val exclude=parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true; val name=parts.drop(2).joinToString("|").ifBlank{parts.getOrNull(1)?.takeUnless{it=="true"||it=="false"} ?: "منطقة"}; list += Zone(a[0],a[1],a[2],a[3],name,exclude) }
        }
    }
    fun saveZones(zones: List<Zone>) = p.edit().putStringSet("zones", zones.map { "${it.left},${it.top},${it.right},${it.bottom}|${it.exclude}|${it.name}" }.toSet()).apply()
    fun clearZones() = p.edit().remove("zones").apply()
}
