package com.facescan.app

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max
import kotlin.math.min

/** Deteksi wajah (ML Kit) + pembuatan embedding (FaceNet). Panggil dari thread latar belakang. */
class FaceEngine(context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.1f)
            .build()
    )
    private val net = FaceNet(context)

    /** Mengembalikan embedding wajah terbesar di gambar, atau null jika tidak ada wajah. */
    @Synchronized
    fun embedLargestFace(bitmap: Bitmap): FloatArray? {
        val faces = try {
            Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
        } catch (e: Exception) {
            return null
        }
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val b = face.boundingBox
        val padX = (b.width() * 0.1f).toInt()
        val padY = (b.height() * 0.1f).toInt()
        val left = max(0, b.left - padX)
        val top = max(0, b.top - padY)
        val right = min(bitmap.width, b.right + padX)
        val bottom = min(bitmap.height, b.bottom + padY)
        if (right - left < 20 || bottom - top < 20) return null
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return net.embed(crop)
    }

    fun close() {
        detector.close()
        net.close()
    }
}
