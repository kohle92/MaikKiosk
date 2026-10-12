package de.maik.kiosk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var overlay: View
    private val handler = Handler(Looper.getMainLooper())
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var previousLuma: Double? = null
    private var lastMotion = System.currentTimeMillis()
    private val sleepAfterMs = 60_000L
    private val motionThreshold = 7.0

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMotionCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web)
        overlay = findViewById(R.id.sleepOverlay)

        val prefs = getSharedPreferences("kiosk", MODE_PRIVATE)
        val startUrl = prefs.getString("url", "https://www.google.de") ?: "https://www.google.de"
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.loadUrl(startUrl)

        overlay.setOnClickListener { wakeDisplay() }
        scheduleSleepCheck()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startMotionCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun scheduleSleepCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() - lastMotion >= sleepAfterMs) overlay.visibility = View.VISIBLE
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun wakeDisplay() {
        lastMotion = System.currentTimeMillis()
        overlay.visibility = View.GONE
    }

    private fun startMotionCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { image ->
                val buffer = image.planes[0].buffer
                val sampleStep = 64
                var sum = 0L; var count = 0; var i = 0
                while (i < buffer.limit()) { sum += (buffer.get(i).toInt() and 0xff); count++; i += sampleStep }
                if (count > 0) {
                    val luma = sum.toDouble() / count
                    previousLuma?.let { old ->
                        if (abs(luma - old) >= motionThreshold) runOnUiThread { wakeDisplay() }
                    }
                    previousLuma = luma
                }
                image.close()
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
