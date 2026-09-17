package com.wwm.arabictranslator

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class ScreenTranslateService:Service(){
    companion object{const val EXTRA_RESULT_CODE="resultCode";const val EXTRA_DATA="data";const val EXTRA_ZONE_MODE="zoneMode";private const val CHANNEL="wwm_translation";private const val NOTIF=42}
    private lateinit var memory:TranslationMemory;private lateinit var glossary:Glossary;private lateinit var settings:TranslatorSettings;private lateinit var recognizer:TextRecognizer;private var translator:Translator?=null
    private val busy=AtomicBoolean(false);private var lastFrame=0L;private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var reader:ImageReader?=null;private var wm:WindowManager?=null;private var overlay:FrameLayout?=null;private val handler=Handler(Looper.getMainLooper());private var callback:MediaProjection.Callback?=null;private var zoneMode=false;private var modelsReady=false

    override fun onCreate(){super.onCreate();memory=TranslationMemory(this);glossary=Glossary(this);settings=TranslatorSettings(this);createChannel();recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);translator=Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(TranslateLanguage.ARABIC).build());prepareModels()}
    private fun prepareModels(){
        val c=DownloadConditions.Builder().build()
        val manager=RemoteModelManager.getInstance()
        val en=TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
        val ar=TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
        manager.download(en,c).addOnSuccessListener{manager.download(ar,c).addOnSuccessListener{modelsReady=true}.addOnFailureListener{modelsReady=false}}
            .addOnFailureListener{modelsReady=false}
    }

    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
        if(projection!=null)return START_NOT_STICKY
        zoneMode=i?.getBooleanExtra(EXTRA_ZONE_MODE,false)?:false
        val rc=i?.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
        val data=if(Build.VERSION.SDK_INT>=33)i?.getParcelableExtra(EXTRA_DATA,Intent::class.java)else @Suppress("DEPRECATION") i?.getParcelableExtra<Intent>(EXTRA_DATA)
        if(rc!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY}
        val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try{
            projection=pm.getMediaProjection(rc,data)
            if(projection==null){stopSelf();return START_NOT_STICKY}
            val notification=NotificationCompat.Builder(this,CHANNEL).setContentTitle("WWM Arabic Translator").setContentText(if(zoneMode)"وضع تحديد المناطق" else "ترجمة الشاشة السريعة").setSmallIcon(android.R.drawable.ic_menu_search).setOngoing(true).build()
            if(Build.VERSION.SDK_INT>=29) startForeground(NOTIF,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(NOTIF,notification)
            callback=object:MediaProjection.Callback(){override fun onStop(){stopCapture()}}
            projection?.registerCallback(callback!!,handler)
            startCapture()
        }catch(_:Throwable){stopCapture();stopSelf()}
        return START_NOT_STICKY
    }

    private fun startCapture(){val m=resources.displayMetrics;val w=m.widthPixels;val h=m.heightPixels;val d=m.densityDpi;reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);display=projection?.createVirtualDisplay("WWMTranslator",w,h,d,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler);if(zoneMode)startZoneSelector();reader?.setOnImageAvailableListener({r->
        if(zoneMode){r.acquireLatestImage()?.close();return@setOnImageAvailableListener}
        val now=SystemClock.elapsedRealtime();if(!settings.enabled||now-lastFrame<settings.frameInterval||!busy.compareAndSet(false,true)){r.acquireLatestImage()?.close();return@setOnImageAvailableListener};lastFrame=now
        val image=r.acquireLatestImage()?:run{busy.set(false);return@setOnImageAvailableListener}
        try{val p=image.planes[0];val pad=(p.rowStride-p.pixelStride*w)/p.pixelStride;val bmp=Bitmap.createBitmap(w+pad,h,Bitmap.Config.ARGB_8888);bmp.copyPixelsFromBuffer(p.buffer);image.close();val crop=Bitmap.createBitmap(bmp,0,0,w,h);bmp.recycle();recognizer.process(InputImage.fromBitmap(crop,0)).addOnSuccessListener{res->
            val blocks=res.textBlocks.asSequence().map{it to clean(it.text)}.filter{(_,s)->s.length in 2..240&&looksEnglish(s)}.filter{(b,_)->shouldTranslate(b.boundingBox,w,h)}.sortedByDescending{dialogueScore(it.first.boundingBox,it.second,w,h)}.take(settings.maxBlocks).toList();crop.recycle();translate(blocks,w,h)
        }.addOnFailureListener{crop.recycle();busy.set(false)}}catch(_:Throwable){try{image.close()}catch(_:Throwable){};busy.set(false)}
    },handler)}

    private fun dialogueScore(r:Rect?,s:String,w:Int,h:Int):Double{if(r==null)return 0.0;val cy=r.centerY().toDouble()/h;val width=r.width().toDouble()/w;val len=(s.length.coerceAtMost(120)/120.0);var score=len+width*0.7;if(cy>0.52)score+=2.5;if(cy>0.68)score+=1.5;return score}
    private fun shouldTranslate(r:Rect?,w:Int,h:Int):Boolean{
        if(r==null)return false
        val cx=r.centerX().toFloat()/w; val cy=r.centerY().toFloat()/h
        val zones=settings.zones()
        if(zones.any{it.exclude && cx in it.left..it.right && cy in it.top..it.bottom}) return false
        val includeZones=zones.filter{!it.exclude}
        return includeZones.isEmpty() || includeZones.any{cx in it.left..it.right && cy in it.top..it.bottom}
    }

    private fun translate(blocks:List<Pair<Text.TextBlock,String>>,w:Int,h:Int){
        getSharedPreferences("wwm_ai", MODE_PRIVATE).edit().putString("last_ocr_text", blocks.joinToString("\n") { it.second }).apply();if(blocks.isEmpty()){handler.post{clearOverlay()};busy.set(false);return};val out=mutableListOf<OverlayItem>();var pending=0;val lock=Any();fun add(item:OverlayItem){synchronized(lock){out.add(item)}};fun done(){synchronized(lock){pending--;if(pending<=0){val snapshot=out.toList();handler.post{show(snapshot,w,h)};busy.set(false)}}}
        if(!modelsReady && blocks.any{glossary.get(it.second)==null && memory.get(it.second)==null}){handler.post{showStatusOverlay("جاري تجهيز نموذج الترجمة…",w,h)};busy.set(false);return}
        for((b,s)in blocks){val custom=glossary.get(s);if(custom!=null){add(OverlayItem(custom,s,b.boundingBox));continue};val cached=memory.get(s);if(cached!=null){add(OverlayItem(cached,s,b.boundingBox));continue};pending++;translator?.translate(s)?.addOnSuccessListener{a->val v=glossary.replaceTerms(a.trim());if(v.isNotEmpty()){memory.put(s,v);add(OverlayItem(v,s,b.boundingBox))};done()}?.addOnFailureListener{done()}?:done()}
        if(pending==0){handler.post{show(out,w,h)};busy.set(false)}
    }

    private fun show(items:List<OverlayItem>,sw:Int,sh:Int){if(!Settings.canDrawOverlays(this))return;handler.post{if(wm==null)wm=getSystemService(WINDOW_SERVICE) as WindowManager;if(overlay==null){overlay=FrameLayout(this);val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP or Gravity.START;wm?.addView(overlay,p)};overlay?.removeAllViews();for(item in items){val r=item.bounds?:continue;val lp=FrameLayout.LayoutParams((r.width()*1.35f).toInt().coerceAtLeast(140),(r.height()*1.9f).toInt().coerceAtLeast(58));lp.leftMargin=r.left;lp.topMargin=r.top;val tv=TextView(this).apply{text=if(settings.showSource)"${item.text}\n${item.source}"else item.text;textSize=settings.fontSize;setTextColor(settings.textColor);setBackgroundColor(settings.backgroundColor);gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_RTL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(settings.bubblePadding,settings.bubblePadding/2,settings.bubblePadding,settings.bubblePadding/2);setLineSpacing(0f,settings.lineSpacing);typeface=when(settings.fontChoice){"serif"->Typeface.SERIF;"mono"->Typeface.MONOSPACE;else->Typeface.SANS_SERIF};alpha=settings.overlayOpacity/100f};overlay?.addView(tv,lp)}}}

    private fun startZoneSelector(){
        if(!Settings.canDrawOverlays(this))return
        handler.post{
            try{overlay?.let{wm?.removeView(it)}}catch(_:Throwable){}
            if(wm==null)wm=getSystemService(WINDOW_SERVICE) as WindowManager
            val container=FrameLayout(this)
            val selector=ZoneSelectorView(this,settings){left,top,right,bottom->
                val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,12);setBackgroundColor(Color.argb(235,25,25,25))}
                val title=TextView(this).apply{text="حفظ المنطقة";textSize=17f;setTextColor(Color.WHITE);textDirection=View.TEXT_DIRECTION_RTL}
                val box=EditText(this).apply{hint="اسم المنطقة (مثلاً: الخريطة)";textDirection=View.TEXT_DIRECTION_RTL;setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY)}
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
                fun actionButton(label:String,exclude:Boolean){
                    row.addView(Button(this@ScreenTranslateService).apply{text=label;setOnClickListener{
                        val list=settings.zones();val name=box.text.toString().trim().ifBlank{"منطقة ${list.size+1}"};list+=TranslatorSettings.Zone(left,top,right,bottom,name,exclude);settings.saveZones(list);startZoneSelector()
                    }},LinearLayout.LayoutParams(0,-2,1f))
                }
                actionButton("استثناء",true);actionButton("ترجمة فقط",false)
                panel.addView(title);panel.addView(box,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8});panel.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})
                container.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{setMargins(12,70,12,0)})
                box.requestFocus()
            }
            container.addView(selector,FrameLayout.LayoutParams(-1,-1))
            val done=Button(this).apply{text="إنهاء";textSize=14f;setOnClickListener{stopSelf()}}
            val dp=(resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val lp=FrameLayout.LayoutParams((92*dp),(-2),Gravity.TOP or Gravity.END);lp.setMargins(0,16*dp,16*dp,0);container.addView(done,lp)
            val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP or Gravity.START;overlay=container;wm?.addView(container,p)
        }
    }

    private fun showStatusOverlay(message:String,sw:Int,sh:Int){
        if(!Settings.canDrawOverlays(this))return
        handler.post{
            if(wm==null)wm=getSystemService(WINDOW_SERVICE) as WindowManager
            if(overlay==null){overlay=FrameLayout(this);val p=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP or Gravity.START;wm?.addView(overlay,p)}
            overlay?.removeAllViews()
            val tv=TextView(this).apply{text=message;textSize=16f;setTextColor(Color.WHITE);setBackgroundColor(Color.argb(210,0,0,0));gravity=Gravity.CENTER;textDirection=View.TEXT_DIRECTION_RTL}
            val lp=FrameLayout.LayoutParams((sw*0.72f).toInt(),(sh*0.10f).toInt().coerceAtLeast(70));lp.leftMargin=((sw-lp.width)/2).coerceAtLeast(0);lp.topMargin=((sh-lp.height)/2).coerceAtLeast(0);overlay?.addView(tv,lp)
        }
    }
    private fun clearOverlay(){overlay?.removeAllViews()}
    private fun clean(s:String)=s.replace(Regex("[\\r\\n\\t]+")," ").replace(Regex("\\s+")," ").trim()
    private fun looksEnglish(s:String):Boolean{val letters=s.count{it.isLetter()};if(letters<2)return false;val latin=s.count{it in 'A'..'Z'||it in 'a'..'z'};return latin.toFloat()/letters>=.55f}
    private fun stopCapture(){reader?.setOnImageAvailableListener(null,null);reader?.close();reader=null;display?.release();display=null;callback?.let{try{projection?.unregisterCallback(it)}catch(_:Throwable){}};projection?.stop();projection=null;clearOverlay()}
    override fun onDestroy(){stopCapture();recognizer.close();translator?.close();overlay?.let{try{wm?.removeView(it)}catch(_:Throwable){}};overlay=null;super.onDestroy()}
    override fun onBind(i:Intent?)=null
    private fun createChannel(){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"WWM Translation",NotificationManager.IMPORTANCE_LOW))}
    private data class OverlayItem(val text:String,val source:String,val bounds:Rect?)

    private class ZoneSelectorView(ctx:Context,private val settings:TranslatorSettings,private val onZone:(Float,Float,Float,Float)->Unit):View(ctx){
        private val paint=Paint(1).apply{style=Paint.Style.STROKE;strokeWidth=5f;color=Color.CYAN};private val fill=Paint(1).apply{style=Paint.Style.FILL;color=Color.argb(55,0,255,255)};private var sx=0f;private var sy=0f;private var ex=0f;private var ey=0f
        override fun onDraw(c:Canvas){super.onDraw(c);c.drawColor(Color.argb(20,0,0,0));for(z in settings.zones()){val r=RectF(z.left*width,z.top*height,z.right*width,z.bottom*height);c.drawRect(r,paint)};if(sx!=ex||sy!=ey){val r=RectF(minOf(sx,ex),minOf(sy,ey),maxOf(sx,ex),maxOf(sy,ey));c.drawRect(r,fill);c.drawRect(r,paint)};paint.style=Paint.Style.FILL;paint.textSize=28f;paint.color=Color.WHITE;c.drawText("اسحب مستطيلاً فوق المنطقة التي تريد إعدادها",24f,48f,paint);paint.style=Paint.Style.STROKE;paint.color=Color.CYAN}
        override fun onTouchEvent(e:android.view.MotionEvent):Boolean{when(e.action){MotionEvent.ACTION_DOWN->{sx=e.x;sy=e.y;ex=sx;ey=sy;invalidate();return true};MotionEvent.ACTION_MOVE->{ex=e.x;ey=e.y;invalidate();return true};MotionEvent.ACTION_UP->{ex=e.x;ey=e.y;val l=minOf(sx,ex)/width;val t=minOf(sy,ey)/height;val r=maxOf(sx,ex)/width;val b=maxOf(sy,ey)/height;if((r-l)>.03f&&(b-t)>.03f)onZone(l,t,r,b);return true}};return true}
    }
}
