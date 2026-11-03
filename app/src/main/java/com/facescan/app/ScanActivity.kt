package com.facescan.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class ScanActivity : AppCompatActivity() {

    private lateinit var engine: FaceEngine
    private lateinit var camera: CameraHelper
    private lateinit var tvResult: TextView
    private var people = listOf<Person>()
    private val busy = AtomicBoolean(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) camera.start() else {
                tvResult.text = "Izin kamera ditolak"
                tvResult.setTextColor(Color.parseColor("#C62828"))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        engine = FaceEngine(this)
        people = FaceStore.load(this)
        tvResult = findViewById(R.id.tvResult)

        camera = CameraHelper(
            this,
            findViewById<PreviewView>(R.id.preview),
            ImageAnalysis.Analyzer { proxy -> analyze(proxy) },
            false
        )
        findViewById<android.view.View>(R.id.btnFlip).setOnClickListener { camera.flip() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.start()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun analyze(proxy: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        val bmp = try {
            proxy.toBitmap().rotate(proxy.imageInfo.rotationDegrees)
        } catch (e: Exception) {
            null
        } finally {
            proxy.close()
        }
        if (bmp == null) {
            busy.set(false)
            return
        }
        val emb = engine.embedLargestFace(bmp)
        val match = emb?.let { FaceStore.bestMatch(it, people) }
        runOnUiThread { show(emb != null, match) }
        busy.set(false)
    }

    private fun show(faceFound: Boolean, match: Match?) {
        when {
            !faceFound || match == null -> {
                tvResult.text = "Arahkan kamera ke wajah"
                tvResult.setTextColor(Color.GRAY)
            }
            match.recognized -> {
                tvResult.text = "✓ ${match.name}\nkemiripan ${match.percent}%"
                tvResult.setTextColor(Color.parseColor("#2E7D32"))
            }
            else -> {
                tvResult.text = "Wajah tidak dikenal\nkemiripan tertinggi ${match.percent}%"
                tvResult.setTextColor(Color.parseColor("#C62828"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        camera.shutdown()
        engine.close()
    }
}
