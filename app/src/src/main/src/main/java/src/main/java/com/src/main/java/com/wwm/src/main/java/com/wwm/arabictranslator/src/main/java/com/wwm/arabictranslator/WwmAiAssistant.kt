package com.wwm.arabictranslator

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * WWM AI is the single in-app assistant entry point.
 * It can work locally for translation/context helpers and can optionally call
 * an OpenAI-compatible endpoint when the user supplies an API key.
 */
class WwmAiAssistant(private val context: Context) {
    private val prefs = context.getSharedPreferences("wwm_ai", Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    fun show() {
        val pad = 20
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val status = TextView(context).apply {
            text = "مساعد WWM — يركز على الترجمة، الحوار، المهام، القصة والتطوير"
            textSize = 15f
            setTextColor(Color.DKGRAY)
            textDirection = View.TEXT_DIRECTION_RTL
        }
        root.addView(status)

        val contextText = EditText(context).apply {
            hint = "النص الحالي من اللعبة (يُملأ تلقائياً إذا توفر)"
            minLines = 3
            maxLines = 7
            textDirection = View.TEXT_DIRECTION_LTR
            setText(lastOcrText())
        }
        root.addView(contextText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })

        val question = EditText(context).apply {
            hint = "اسأل مثلاً: ما المطلوب مني في هذه المهمة؟"
            minLines = 2
            textDirection = View.TEXT_DIRECTION_RTL
        }
        root.addView(question, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })

        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val ask = Button(context).apply { text = "اسأل WWM AI" }
        val explain = Button(context).apply { text = "فهم النص" }
        val translate = Button(context).apply { text = "شرح الترجمة" }
        actions.addView(ask, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(explain, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(translate, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })

        val result = TextView(context).apply {
            textSize = 16f
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(Color.DKGRAY)
            setPadding(10, 16, 10, 10)
        }
        root.addView(result, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 10 })

        val settings = Button(context).apply { text = "إعداد AI (اختياري)" }
        root.addView(settings)

        ask.setOnClickListener {
            val q = question.text.toString().trim()
            val source = contextText.text.toString().trim()
            if (q.isBlank()) { result.text = "اكتب سؤالك أولاً."; return@setOnClickListener }
            runAi(q, source, result)
        }
        explain.setOnClickListener {
            val source = contextText.text.toString().trim()
            result.text = localExplain(source)
        }
        translate.setOnClickListener {
            val source = contextText.text.toString().trim()
            result.text = localTranslationGuide(source)
        }
        settings.setOnClickListener { showAiSettings() }

        AlertDialog.Builder(context)
            .setTitle("🧠 WWM AI")
            .setView(root)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun lastOcrText(): String = prefs.getString("last_ocr_text", "") ?: ""

    private fun runAi(question: String, source: String, output: TextView) {
        val key = prefs.getString("api_key", "") ?: ""
        val endpoint = prefs.getString("endpoint", "") ?: ""
        if (key.isBlank() || endpoint.isBlank()) {
            output.text = localAnswer(question, source)
            return
        }
        output.text = "جاري تحليل النص…"
        executor.execute {
            try {
                val answer = callCompatibleApi(endpoint, key, question, source)
                output.post { output.text = answer }
            } catch (e: Exception) {
                output.post { output.text = "تعذر الاتصال بالذكاء الاصطناعي. استخدمت الوضع المحلي بدلاً منه.\n\n${localAnswer(question, source)}" }
            }
        }
    }

    private fun localAnswer(q: String, source: String): String {
        val s = source.ifBlank { "لا يوجد نص مقروء حالياً." }
        return when {
            q.contains("مهم", true) || q.contains("مهمة", true) || q.contains("quest", true) ->
                "وضع المهمة: سأركز على استخراج المطلوب، الأهداف، الشخصيات والأماكن من النص الظاهر.\n\nالنص المقروء:\n$s\n\nللحصول على تحليل أعمق للمهام والقصة، فعّل مزود AI من إعدادات WWM AI."
            q.contains("قص", true) || q.contains("story", true) || q.contains("قصة", true) ->
                "وضع القصة: سأتعامل مع النص كجزء من سياق القصة وأفصل الشخصيات والأحداث والمطلوب الحالي.\n\nالنص:\n$s"
            q.contains("تطو", true) || q.contains("skill", true) || q.contains("مهار", true) ->
                "وضع التطوير: سأركز على اسم المهارة/السلاح، تأثيره، ومتطلبات التطوير إذا ظهرت في النص.\n\nالنص:\n$s"
            else -> "تحليل محلي أولي:\n\n$s\n\nيمكن ربط WWM AI بمزود نموذج متوافق من إعدادات AI للحصول على فهم سياقي أوسع."
        }
    }

    private fun localExplain(source: String): String {
        if (source.isBlank()) return "لم يتم التقاط نص حالياً. افتح اللعبة ودع OCR يقرأ الحوار أولاً."
        return "فهم سريع للنص:\n\n$source\n\nسأعطي أولوية للحوار والأهداف والأسماء، وأتجنب تحويل عناصر HUD غير المهمة إلى ترجمة."
    }

    private fun localTranslationGuide(source: String): String {
        if (source.isBlank()) return "لا يوجد نص للترجمة حالياً."
        return "النص المراد ترجمته:\n\n$source\n\nملاحظة: الترجمة الفعلية داخل مسار OCR تستخدم محرك English → Arabic وذاكرة الترجمة والقاموس؛ هذا الزر مخصص للفهم والأسئلة والتحليل."
    }

    private fun showAiSettings() {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 8, 20, 8) }
        val endpoint = EditText(context).apply {
            hint = "رابط OpenAI-compatible API"
            setText(prefs.getString("endpoint", "") ?: "")
            textDirection = View.TEXT_DIRECTION_LTR
        }
        val key = EditText(context).apply {
            hint = "API Key (اختياري)"
            setText(prefs.getString("api_key", "") ?: "")
            inputType = 0x00000081
            textDirection = View.TEXT_DIRECTION_LTR
        }
        box.addView(endpoint)
        box.addView(key, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 8 })
        AlertDialog.Builder(context)
            .setTitle("إعداد WWM AI")
            .setMessage("بدون مفتاح API يعمل المساعد بوضع محلي محدود. عند إضافة مزود متوافق، يرسل التطبيق النص الذي تختاره أنت فقط للتحليل.")
            .setView(box)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit().putString("endpoint", endpoint.text.toString().trim()).putString("api_key", key.text.toString().trim()).apply()
            }.show()
    }

    private fun callCompatibleApi(endpoint: String, apiKey: String, question: String, source: String): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        val system = "You are WWM AI, a careful Arabic assistant specialized in the mobile game Where Winds Meet. Help with translation, dialogue meaning, quests, story context, character names, locations, skills and upgrades. Never invent game facts. If the supplied text is insufficient, say so. Prefer concise Iraqi-friendly Arabic."
        val body = JSONObject()
            .put("model", prefs.getString("model", "gpt-4o-mini"))
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", "Game text:\n$source\n\nQuestion:\n$question")))
            .put("temperature", 0.2)
            .toString()
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        val json = JSONObject(response)
        return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: json.optString("output", response)
    }
}
