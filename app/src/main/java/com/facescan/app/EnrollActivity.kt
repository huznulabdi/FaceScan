package com.facescan.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class EnrollActivity : AppCompatActivity() {

    private lateinit var engine: FaceEngine
    private lateinit var camera: CameraHelper
    private lateinit var etName: EditText
    private lateinit var tvStatus: TextView
    private val captureRequested = AtomicBoolean(false)

    @Volatile
    private var pendingName = ""

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) camera.start() else tvStatus.text = "Izin kamera ditolak. Anda masih bisa memakai foto dari Galeri."
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) enrollFromGallery(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll)

        engine = FaceEngine(this)
        etName = findViewById(R.id.etName)
        tvStatus = findViewById(R.id.tvStatus)

        camera = CameraHelper(
            this,
            findViewById<PreviewView>(R.id.preview),
            ImageAnalysis.Analyzer { proxy -> analyze(proxy) },
            true
        )

        findViewById<android.view.View>(R.id.btnShot).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                toast("Isi nama dulu")
            } else {
                pendingName = name
                tvStatus.text = "Memproses..."
                captureRequested.set(true)
            }
        }
        findViewById<android.view.View>(R.id.btnGallery).setOnClickListener {
            if (etName.text.toString().trim().isEmpty()) toast("Isi nama dulu")
            else galleryLauncher.launch("image/*")
        }
        findViewById<android.view.View>(R.id.btnFlip).setOnClickListener { camera.flip() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.start()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun analyze(proxy: ImageProxy) {
        if (!captureRequested.compareAndSet(true, false)) {
            proxy.close()
            return
        }
        val bmp: Bitmap? = try {
            proxy.toBitmap().rotate(proxy.imageInfo.rotationDegrees)
        } catch (e: Exception) {
            null
        } finally {
            proxy.close()
        }
        saveFace(pendingName, bmp)
    }

    private fun enrollFromGallery(uri: Uri) {
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            toast("Isi nama dulu")
            return
        }
        tvStatus.text = "Memproses foto..."
        Thread { saveFace(name, loadBitmap(this, uri)) }.start()
    }

    /** Dijalankan di thread latar belakang. */
    private fun saveFace(name: String, bmp: Bitmap?) {
        val emb = bmp?.let { engine.embedLargestFace(it) }
        val count = if (emb != null) FaceStore.addEmbedding(this, name, emb) else 0
        runOnUiThread {
            tvStatus.text = if (emb == null) {
                "Wajah tidak terdeteksi. Pastikan wajah terlihat jelas dan cukup terang, lalu coba lagi."
            } else {
                "✓ $name tersimpan ($count foto). Tambah foto dari sudut/cahaya lain agar lebih akurat."
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        camera.shutdown()
        engine.close()
    }
}
