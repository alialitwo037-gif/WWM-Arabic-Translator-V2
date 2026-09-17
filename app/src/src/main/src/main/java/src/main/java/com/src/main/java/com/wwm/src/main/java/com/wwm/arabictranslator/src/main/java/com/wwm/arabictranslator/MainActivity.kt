package com.wwm.arabictranslator

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    companion object { private const val REQ_CAPTURE=7001; private const val REQ_ZONE_CAPTURE=7002; private const val REQ_EXPORT=7101; private const val REQ_IMPORT=7102 }
    private lateinit var memory:TranslationMemory; private lateinit var glossary:Glossary; private lateinit var settings:TranslatorSettings; private lateinit var status:TextView

    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),9001);memory=TranslationMemory(this);glossary=Glossary(this);settings=TranslatorSettings(this);buildUi()}
    override fun onResume(){super.onResume();if(::status.isInitialized)updateStatus()}

    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,26,24,24);setBackgroundColor(Color.WHITE)}
        val scroll=ScrollView(this).apply{addView(root)}
        root.addView(TextView(this).apply{text="WWM Arabic Translator — V1.4 Deep Audited";textSize=25f;setTextColor(Color.BLACK);gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_RTL;setPadding(0,0,0,12)},lp())
        status=TextView(this).apply{textSize=14f;setTextColor(Color.DKGRAY);textDirection=View.TEXT_DIRECTION_RTL;setPadding(0,8,0,16)};root.addView(status,lp())
        root.addView(TextView(this).apply{text="مترجم شاشة مخصص لنسخة Where Winds Meet على الموبايل. أولوية الحوار، ذاكرة ترجمة دقيقة، قاموس مخصص، مناطق ترجمة واستثناء، وتحكم كامل بالشكل.";textSize=16f;setTextColor(Color.DKGRAY);textDirection=View.TEXT_DIRECTION_RTL;setPadding(0,0,0,18)},lp())
        root.addView(button("① السماح بالظهور فوق اللعبة"){startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))},lp())
        root.addView(button("② بدء الترجمة — وضع الحوار السريع"){requestCapture(false)},lp())
        root.addView(button("③ تحديد مناطق الترجمة / الاستثناء بالسحب"){requestCapture(true)},lp())
        root.addView(button("④ السرعة + الخط + اللون + الخلفية + الشكل"){showSettings()},lp())
        root.addView(button("⑤ عرض المناطق المحفوظة / مسحها"){showZones()},lp())
        root.addView(button("⑥ تعديل ترجمة محفوظة يدوياً"){editMemory()},lp())
        root.addView(button("⑦ قاموس أسماء الشخصيات / الأماكن / المهارات"){editGlossary()},lp())
        root.addView(button("⑧ استيراد / تصدير ذاكرة الترجمة"){showBackup()},lp())
        root.addView(button("⑨ دليل بصري لنسخة الموبايل"){showGuide()},lp())
        root.addView(button("⑩ 📖 دليل الاستخدام الكامل"){showFullGuide()},lp())
        root.addView(button("🧠 WWM AI — مساعد اللعبة"){ WwmAiAssistant(this).show() },lp())
        root.addView(button("إيقاف الترجمة"){stopService(Intent(this,ScreenTranslateService::class.java))},lp())
        root.addView(button("مسح ذاكرة الترجمة"){confirmClearMemory()},lp())
        updateStatus();setContentView(scroll)
    }

    private fun showSettings(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,8)}
        val speedLabel=TextView(this).apply{textSize=14f;setTextColor(Color.DKGRAY);textDirection=View.TEXT_DIRECTION_RTL;setPadding(0,8,0,2);text="سرعة OCR والفحص: ${settings.frameInterval}ms"};box.addView(speedLabel)
        val speed=SeekBar(this).apply{max=138;progress=((1500-settings.frameInterval)/10).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(f){settings.frameInterval=(1500-p*10).coerceIn(120,1500).toLong();speedLabel.text="سرعة OCR والفحص: ${settings.frameInterval}ms"}};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){} })};box.addView(speed)
        addLabel(box,"حجم الخط: ${settings.fontSize.toInt()}");val size=SeekBar(this).apply{max=30;progress=(settings.fontSize-12).toInt();setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(f)settings.fontSize=(p+12).toFloat()};override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){} })};box.addView(size)
        addLabel(box,"نوع الخط");val fonts=arrayOf("sans" to "Sans","serif" to "Serif","mono" to "Monospace");box.addView(Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,fonts.map{it.second});setSelection(fonts.indexOfFirst{it.first==settings.fontChoice}.coerceAtLeast(0));onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){settings.fontChoice=fonts[pos].first}}})
        addLabel(box,"لون النص");box.addView(colorRow(false));addLabel(box,"لون الخلفية");box.addView(colorRow(true))
        addLabel(box,"شفافية الترجمة: ${settings.overlayOpacity}%")
        val opacity=SeekBar(this).apply {
            max=80
            progress=settings.overlayOpacity-20
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(f)settings.overlayOpacity=p+20}
                override fun onStartTrackingTouch(s:SeekBar?){ }
                override fun onStopTrackingTouch(s:SeekBar?){ }
            })
        }
        box.addView(opacity)
        val src=CheckBox(this).apply{text="إظهار النص الإنكليزي الصغير تحت العربي";isChecked=settings.showSource;setOnCheckedChangeListener{_,v->settings.showSource=v}};box.addView(src)
        addLabel(box,"أقصى عدد كتل نصية بالدورة: ${settings.maxBlocks}")
        val maxBlocks=SeekBar(this).apply {
            max=11
            progress=settings.maxBlocks-1
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar?,p:Int,f:Boolean){if(f)settings.maxBlocks=p+1}
                override fun onStartTrackingTouch(s:SeekBar?){ }
                override fun onStopTrackingTouch(s:SeekBar?){ }
            })
        }
        box.addView(maxBlocks)
        AlertDialog.Builder(this).setTitle("إعدادات متقدمة").setView(box).setPositiveButton("حفظ",null).show()
    }

    private fun editMemory(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,8)};val source=EditText(this).apply{hint="النص الإنكليزي";textDirection=View.TEXT_DIRECTION_LTR};val target=EditText(this).apply{hint="الترجمة العربية";textDirection=View.TEXT_DIRECTION_RTL};box.addView(source,lp());box.addView(target,lp())
        AlertDialog.Builder(this).setTitle("تعديل ترجمة محفوظة").setMessage("هذه العملية تعدّل المطابقة الدقيقة فقط؛ لا يوجد استبدال تقريبي قد يخلط بين حوارين.").setView(box).setNegativeButton("إلغاء",null).setPositiveButton("حفظ"){_,_->if(source.text.isNotBlank()&&target.text.isNotBlank()){memory.put(source.text.toString(),target.text.toString());updateStatus()}}.show()
    }

    private fun editGlossary(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,8,20,8)};val source=EditText(this).apply{hint="اسم/مصطلح إنكليزي";textDirection=View.TEXT_DIRECTION_LTR};val target=EditText(this).apply{hint="الترجمة العربية الثابتة";textDirection=View.TEXT_DIRECTION_RTL};box.addView(source,lp());box.addView(target,lp());
        val current=glossary.all().entries.take(30).joinToString("\n"){it.key+"  ←  "+it.value};if(current.isNotBlank())box.addView(TextView(this).apply{text="\nأمثلة محفوظة:\n$current";textDirection=View.TEXT_DIRECTION_RTL;textSize=13f})
        AlertDialog.Builder(this).setTitle("القاموس المخصص").setMessage("القاموس له أولوية على ذاكرة الترجمة؛ مناسب لأسماء الشخصيات والأماكن والمهارات.").setView(box).setNegativeButton("مسح القاموس"){_,_->glossary.clear();updateStatus()}.setPositiveButton("حفظ"){_,_->if(source.text.isNotBlank()&&target.text.isNotBlank()){glossary.put(source.text.toString(),target.text.toString());updateStatus()}}.show()
    }

    private fun showBackup(){
        val items=arrayOf("تصدير ذاكرة الترجمة","استيراد ذاكرة الترجمة","تصدير القاموس","استيراد القاموس")
        AlertDialog.Builder(this).setTitle("النسخ الاحتياطي").setItems(items){_,which->when(which){0->createDocument("wwm_translation_memory.json");1->openDocument();2->createDocument("wwm_glossary.json");3->openDocument(glossary=true)}}.show()
    }
    private var pendingExportGlossary=false;private var pendingImportGlossary=false
    private fun createDocument(name:String){pendingExportGlossary=name.contains("glossary");startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/json";putExtra(Intent.EXTRA_TITLE,name)},REQ_EXPORT)}
    private fun openDocument(glossary:Boolean=false){pendingImportGlossary=glossary;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/json";addCategory(Intent.CATEGORY_OPENABLE)},REQ_IMPORT)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data?.data==null)return;try{when(requestCode){REQ_EXPORT->contentResolver.openOutputStream(data.data!!)?.use{os->val text=if(pendingExportGlossary)exportGlossary() else memory.exportJson();os.write(text.toByteArray(Charsets.UTF_8))};REQ_IMPORT->contentResolver.openInputStream(data.data!!)?.use{ins->val text=BufferedReader(InputStreamReader(ins,Charsets.UTF_8)).readText();if(pendingImportGlossary)importGlossary(text)else memory.importJson(text);updateStatus()};REQ_CAPTURE,REQ_ZONE_CAPTURE->handleCapture(requestCode,resultCode,data)}}catch(e:Exception){Toast.makeText(this,"فشل العملية: ${e.message}",Toast.LENGTH_LONG).show()}}
    private fun exportGlossary():String{val a=org.json.JSONArray();for((k,v)in glossary.all())a.put(org.json.JSONObject().put("source",k).put("translation",v));return org.json.JSONObject().put("version",1).put("items",a).toString(2)}
    private fun importGlossary(text:String){val a=org.json.JSONObject(text).optJSONArray("items")?:return;for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val s=o.optString("source");val t=o.optString("translation");if(s.isNotBlank()&&t.isNotBlank())glossary.put(s,t)}}

    private fun requestCapture(zoneMode:Boolean){val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;startActivityForResult(manager.createScreenCaptureIntent(),if(zoneMode)REQ_ZONE_CAPTURE else REQ_CAPTURE)}
    private fun handleCapture(requestCode:Int,resultCode:Int,data:Intent){val service=Intent(this,ScreenTranslateService::class.java).apply{putExtra(ScreenTranslateService.EXTRA_RESULT_CODE,resultCode);putExtra(ScreenTranslateService.EXTRA_DATA,data);putExtra(ScreenTranslateService.EXTRA_ZONE_MODE,requestCode==REQ_ZONE_CAPTURE)};ContextCompat.startForegroundService(this,service)}

    private fun showZones(){val z=settings.zones();val names=z.mapIndexed{i,v->"${i+1}. ${v.name} — ${if(v.exclude) "استثناء" else "ترجمة فقط"} (${(v.left*100).toInt()}%, ${(v.top*100).toInt()}%)"}.joinToString("\n");AlertDialog.Builder(this).setTitle("المناطق المحفوظة").setMessage(if(names.isBlank())"لا توجد مناطق. من زر التحديد اسحب مستطيلاً على الشاشة ثم اكتب اسماً للمنطقة." else names).setNegativeButton("مسح الكل"){_,_->settings.clearZones();updateStatus()}.setPositiveButton("تم",null).show()}
    private fun showGuide(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(12,4,12,4)}
        box.addView(TextView(this).apply{text="دليل موبايل WWM\n\nنسخة اللعبة على Android/iOS لها واجهة مخصصة للموبايل ويمكن تحريك أزرارها وتغيير حجمها وشفافيتها. لذلك نعتمد في المترجم على إحداثيات نسبية للشاشة، وليس على دقة هاتف واحدة.";textDirection=View.TEXT_DIRECTION_RTL;textSize=15f})
        box.addView(TextView(this).apply{text="\n🟢 يترجم: منطقة حوار NPC أو نص مهمة.\n🔴 لا يترجم: الخريطة، أزرار القتال، شريط الحالة، وأي منطقة تختارها أنت.\n🟡 أولوية عالية: الحوار في النصف السفلي من الشاشة.";textDirection=View.TEXT_DIRECTION_RTL;textSize=15f})
        box.addView(TextView(this).apply{text="\nالصور التالية مأخوذة من صفحة Google Play الرسمية لنسخة Android وتُستخدم كمرجع بصري لتخطيط الموبايل. المناطق الملونة في الشرح داخل التطبيق تحدد أين نسمح بالترجمة وأين نمنعها.";textDirection=View.TEXT_DIRECTION_RTL;textSize=14f})
        val urls=listOf("https://play-lh.googleusercontent.com/wTCyR097pfdyZ-4tVR2cPg38UICf-41oiCBUsHOr5ei3fkBttlIAIuXJqDlce2PAHq2FwpjxM6QxYFjXUYBV%3Dw526-h296","https://play-lh.googleusercontent.com/Er_qrQukDMYKptonGrhfbNY-8qtgPT2Pt7TfD39_O1l1du5APeGHSLFxztP-usShQoyuYNffE8g_wEmY6Q4CPQ%3Dw526-h296")
        urls.forEachIndexed{index,url->
            val iv=ImageView(this).apply{adjustViewBounds=true;setPadding(0,12,0,12);contentDescription="لقطة موبايل Where Winds Meet ${index+1}"}
            box.addView(TextView(this).apply{text=if(index==0)"\nمرجع موبايل 1 — لا نترجم عناصر الواجهة الثابتة." else "\nمرجع موبايل 2 — نركز على النصوص القصصية والحوار.";textDirection=View.TEXT_DIRECTION_RTL;textSize=14f})
            box.addView(iv,LinearLayout.LayoutParams(-1,-2))
            Thread{try{val bmp=android.graphics.BitmapFactory.decodeStream(java.net.URL(url).openStream());iv.post{iv.setImageBitmap(bmp)}}catch(_:Throwable){iv.post{iv.setImageResource(android.R.drawable.ic_menu_report_image)}}}.start()
        }
        AlertDialog.Builder(this).setTitle("دليل بصري — نسخة الموبايل").setView(box).setNegativeButton("فتح صفحة اللعبة"){_,_->startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.netease.yysls")))}.setPositiveButton("تم",null).show()
    }
    private fun showFullGuide(){
        val scroll=ScrollView(this)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,18,24,24)}
        val guide="""
📖 دليل الاستخدام الكامل — WWM Arabic Translator

1) أول تشغيل
• افتح التطبيق.
• اضغط «السماح بالظهور فوق اللعبة» وفعّل السماح.
• على Android 13+ اسمح بإشعارات التطبيق حتى تظهر حالة خدمة الترجمة.

2) بدء الترجمة
• افتح Where Winds Meet.
• ارجع للتطبيق واضغط «بدء الترجمة — وضع الحوار السريع».
• وافق على تسجيل/مشاركة الشاشة عندما يطلب Android ذلك.
• اترك الخدمة تعمل ثم ارجع للعبة.
• أول مرة قد يحتاج محرك الترجمة إلى تنزيل نموذج اللغة؛ أثناء ذلك انتظر حتى يصبح النموذج جاهزاً.

3) كيف تعمل الترجمة؟
الشاشة → OCR → ترتيب أولوية الحوار → القاموس/ذاكرة الترجمة → ترجمة English→Arabic → ظهور العربية قرب النص.
الجمل المطابقة تماماً لذاكرة الترجمة تستخدم الترجمة المحفوظة فوراً.

4) مناطق الترجمة والاستثناء
• اضغط «تحديد مناطق الترجمة / الاستثناء بالسحب».
• اسحب مستطيلاً فوق المنطقة المطلوبة.
• اختر «استثناء» إذا تريد منع OCR/الترجمة داخلها.
• اختر «ترجمة فقط» إذا تريد قصر الترجمة على هذه المنطقة.
• يمكن حفظ عدة مناطق.
• إذا أنشأت منطقة «ترجمة فقط»، سيُسمح بالترجمة داخل مناطق الترجمة فقط، مع بقاء مناطق الاستثناء ممنوعة.

5) إعدادات السرعة والشكل
• سرعة OCR: قيمة أقل = فحص أكثر تكراراً واستهلاك أعلى؛ قيمة أعلى = أخف وأبطأ.
• حجم الخط: اضبطه حسب دقة الشاشة.
• نوع الخط: Sans / Serif / Monospace.
• لون النص والخلفية والشفافية.
• إظهار النص الإنكليزي تحت العربي عند الحاجة للمراجعة.
• أقصى عدد كتل نصية بالدورة: ارفعها إذا كانت الشاشة تحتوي أكثر من حوار، وخفضها إذا أردت تقليل الحمل.

6) ذاكرة الترجمة
• كل ترجمة جديدة ناجحة يمكن حفظها محلياً.
• عند ظهور النص نفسه مرة أخرى، تُستخدم الترجمة المحفوظة.
• «تعديل ترجمة محفوظة» يسمح لك بتصحيح جملة محددة.
• «مسح ذاكرة الترجمة» يحذف الذاكرة فقط ولا يحذف القاموس.

7) القاموس المخصص
استخدمه لأسماء الشخصيات والأماكن والمهارات والمصطلحات التي تريدها بترجمة ثابتة.
القاموس له أولوية على ذاكرة الترجمة.

8) النسخ الاحتياطي
من «استيراد / تصدير ذاكرة الترجمة» يمكنك:
• تصدير/استيراد Translation Memory بصيغة JSON.
• تصدير/استيراد القاموس بصيغة JSON.
اختر مكان الحفظ في مدير الملفات.

9) WWM AI
• اضغط «🧠 WWM AI — مساعد اللعبة».
• سيظهر آخر نص التقطه OCR تلقائياً إن وجد.
• اكتب سؤالاً مثل: «شنو المطلوب مني؟» أو «اشرح هذا الحوار».
• بدون مزود خارجي يعمل الوضع المحلي المحدود.
• عند إعداد endpoint + API key متوافق، يمكن إرسال النص والسؤال للتحليل الخارجي.
• زر «شرح الترجمة» يشرح مسار الترجمة داخل التطبيق ولا يدّعي أنه قاعدة بيانات كاملة للعبة.
• لا يعتبر المساعد حالياً قاعدة بيانات كاملة لكل محتوى اللعبة؛ دقة معرفة مهمة أو قصة محددة تعتمد على المعلومات المتاحة له.

10) الإيقاف
اضغط «إيقاف الترجمة» من التطبيق. عند الإيقاف تُغلق جلسة التقاط الشاشة وتختفي الترجمة.

11) نصائح للدقة والأداء
• ابدأ بسرعة 250–400ms.
• ركّز منطقة الترجمة على الحوار إذا كان الـHUD يسبب ضوضاء.
• ارفع maxBlocks فقط عند الحاجة.
• إذا كان الحوار سريعاً جداً، اعتمد على ذاكرة الترجمة بعد أول ظهور.
• OCR يعتمد على وضوح النص وحجمه وتباينه، لذلك لا يمكن ضمان قراءة كل مؤثر بصري.

12) الخصوصية
الـOCR والذاكرة يعملان محلياً داخل التطبيق. النص لا يُرسل إلى مزود AI خارجي إلا إذا أدخلت endpoint + API key واستخدمت وظيفة السؤال الخارجية.
        """.trimIndent()
        box.addView(TextView(this).apply{text=guide;textSize=16f;textDirection=View.TEXT_DIRECTION_RTL;setTextColor(Color.DKGRAY)})
        scroll.addView(box)
        AlertDialog.Builder(this).setTitle("📖 دليل الاستخدام الكامل").setView(scroll).setPositiveButton("فهمت",null).show()
    }

    private fun colorRow(bg:Boolean):LinearLayout{val row=LinearLayout(this);val colors=listOf(Color.WHITE,Color.YELLOW,Color.CYAN,Color.GREEN,Color.RED,Color.BLACK);colors.forEach{c->row.addView(Button(this).apply{setBackgroundColor(c);text="  ";setOnClickListener{if(bg)settings.backgroundColor=Color.argb(190,Color.red(c),Color.green(c),Color.blue(c))else settings.textColor=c}},LinearLayout.LayoutParams(0,54,1f))};return row}
    private fun addLabel(box:LinearLayout,t:String){box.addView(TextView(this).apply{text=t;textSize=14f;setTextColor(Color.DKGRAY);textDirection=View.TEXT_DIRECTION_RTL;setPadding(0,8,0,2)})}
    private fun confirmClearMemory(){AlertDialog.Builder(this).setTitle("مسح الذاكرة؟").setMessage("سيتم حذف الترجمات المحفوظة فقط، ولن يحذف القاموس.").setNegativeButton("إلغاء",null).setPositiveButton("مسح"){_,_->memory.clear();updateStatus()}.show()}
    private fun updateStatus(){status.text="الذاكرة: ${memory.count()} • القاموس: ${glossary.all().size} • السرعة: ${settings.frameInterval}ms • المناطق: ${settings.zones().size}"}
    private fun button(label:String,action:()->Unit)=Button(this).apply{text=label;textSize=15f;setOnClickListener{action()}}
    private fun lp()=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,6,0,6)}
}
