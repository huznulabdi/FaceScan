package com.facescan.app

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.sqrt

/** Mengubah gambar wajah menjadi vektor 128 angka (embedding) memakai model FaceNet TFLite. */
class FaceNet(context: Context) {

    private val interpreter: Interpreter

    init {
        val fd = context.assets.openFd(MODEL)
        val buffer = FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        interpreter = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(4) })
    }

    @Synchronized
    fun embed(face: Bitmap): FloatArray {
        val bmp = Bitmap.createScaledBitmap(face, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        bmp.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        val n = SIZE * SIZE * 3
        val values = FloatArray(n)
        var i = 0
        for (p in pixels) {
            values[i++] = ((p shr 16) and 0xFF).toFloat()
            values[i++] = ((p shr 8) and 0xFF).toFloat()
            values[i++] = (p and 0xFF).toFloat()
        }

        // Standarisasi per gambar (mean 0, std 1)
        var mean = 0f
        for (v in values) mean += v
        mean /= n
        var variance = 0f
        for (v in values) variance += (v - mean) * (v - mean)
        val std = max(sqrt(variance / n), 1f / sqrt(n.toFloat()))

        val input = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
        for (v in values) input.putFloat((v - mean) / std)
        input.rewind()

        val output = Array(1) { FloatArray(DIM) }
        interpreter.run(input, output)
        return l2Normalize(output[0])
    }

    fun close() = interpreter.close()

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = max(sqrt(sum), 1e-10f)
        return FloatArray(v.size) { v[it] / norm }
    }

    companion object {
        private const val MODEL = "facenet.tflite"
        private const val SIZE = 160
        const val DIM = 128
    }
}
