package com.wwm.arabictranslator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TranslationMemory(context: Context) {
    private val prefs = context.getSharedPreferences("translation_memory", Context.MODE_PRIVATE)
    fun normalize(text: String) = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
    fun get(source: String): String? = prefs.getString(normalize(source), null)
    fun put(source: String, arabic: String) = prefs.edit().putString(normalize(source), arabic.trim()).apply()
    fun count(): Int = prefs.all.size
    fun all(): Map<String,String> = prefs.all.mapNotNull{(k,v)->(v as? String)?.let{k to it}}.toMap()
    fun clear() = prefs.edit().clear().apply()
    fun exportJson():String {
        val a=JSONArray(); for((k,v) in all()) a.put(JSONObject().put("source",k).put("translation",v))
        return JSONObject().put("version",1).put("items",a).toString(2)
    }
    fun importJson(text:String):Int {
        val root=JSONObject(text); val a=root.optJSONArray("items")?:return 0; var n=0
        for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue; val s=o.optString("source"); val t=o.optString("translation"); if(s.isNotBlank()&&t.isNotBlank()){put(s,t);n++}}
        return n
    }
}
