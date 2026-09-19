package com.wwm.arabictranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            try {
                val serviceIntent = Intent(this, ScreenTranslateService::class.java).apply {
                    putExtra(ScreenTranslateService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenTranslateService.EXTRA_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "تم تشغيل الخدمة بنجاح", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                saveCrashLog(e)
                Toast.makeText(this, "خطأ بالخدمة: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "تم إلغاء الإذن", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // التقاط أي انهيار غير متوقع وحفظه في ملف
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            saveCrashLog(throwable)
        }

        super.onCreate(savedInstanceState)

        try {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                gravity = Gravity.CENTER
            }

            val btnStart = Button(this).apply {
                text = "تشغيل مترجم الشاشة"
                setOnClickListener {
                    checkAndStartService()
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }

            root.addView(btnStart, lp)
            setContentView(root)

        } catch (e: Throwable) {
            saveCrashLog(e)
            Toast.makeText(this, "حدث خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndStartService() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "يرجى منح إذن الظهور أولاً", Toast.LENGTH_LONG).show()
                return
            }

            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
        } catch (e: Exception) {
            saveCrashLog(e)
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val logFile = File(dir, "crash_log.txt")
            val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            FileWriter(logFile, true).use { writer ->
                writer.append("\n\n=== CRASH AT $timeStamp ===\n")
                throwable.printStackTrace(PrintWriter(writer))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
