package com.idr.hnsw.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    const val VECTOR_DIM = 64 * 64

    fun bitmapToVector(bitmap: Bitmap): FloatArray {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val vector = FloatArray(VECTOR_DIM)
        
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                val pixel = scaledBitmap.getPixel(x, y)
                // Convert to grayscale: 0.299R + 0.587G + 0.114B
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toFloat() / 255.0f
                vector[y * 64 + x] = gray
            }
        }
        return vector
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, filename: String): File {
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    fun loadBitmap(context: Context, filename: String): Bitmap? {
        val file = File(context.filesDir, filename)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }
}
