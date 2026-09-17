package com.wwm.arabictranslator

import android.content.Context
import java.util.Locale

class Glossary(context: Context) {
    private val p=context.getSharedPreferences("wwm_glossary", Context.MODE_PRIVATE)
    private fun n(s:String)=s.lowercase(Locale.ROOT).replace(Regex("\\s+")," ").trim()
    fun get(source:String):String?=p.getString(n(source),null)
    fun put(source:String,target:String)=p.edit().putString(n(source),target.trim()).apply()
    fun remove(source:String)=p.edit().remove(n(source)).apply()
    fun all(): Map<String, String> =p.all.mapNotNull{(k,v)->(v as? String)?.let{k to it}}.toMap()
    fun replaceTerms(text:String):String {
        var out=text
        all().entries.sortedByDescending{it.key.length}.forEach { (source,target) ->
            if(source.isNotBlank()) out=out.replace(Regex(Regex.escape(source), RegexOption.IGNORE_CASE), target)
        }
        return out
    }
    fun clear()=p.edit().clear().apply()
}
