package com.wwm.arabictranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : Activity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenTranslateService::class.java).apply {
                putExtra(ScreenTranslateService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenTranslateService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "تم تشغيل خدمة الترجمة", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "تم إلغاء إذن التقاط الشاشة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val root = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                gravity = android.view.Gravity.CENTER
            }

            val btnStart = Button(this).apply {
                text = "تشغيل مترجم الشاشة"
                setOnClickListener {
                    checkAndStartService()
                }
            }

            val btnAi = Button(this).apply {
                text = "فتح مساعد WWM AI"
                setOnClickListener {
                    try {
                        WwmAiAssistant(this@MainActivity).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "خطأ في تشغيل AI: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            root.addView(btnStart)
            root.addView(btnAi)
            setContentView(root)

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "حدث خطأ أثناء التهيئة: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndStartService() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "يرجى منح إذن الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            return
        }

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }
}
