package com.mhirex.editor.services

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder

/** Runs on-device person segmentation and returns a transparent PNG-ready bitmap. */
object BackgroundRemovalProcessor {
    fun remove(context: Context, source: Bitmap): Bitmap {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        val segmenter = Segmentation.getClient(options)
        try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(source, 0)))
            val maskBuffer = mask.buffer.order(ByteOrder.nativeOrder())
            val maskWidth = mask.width
            val maskHeight = mask.height
            val pixels = IntArray(source.width * source.height)
            for (y in 0 until source.height) {
                val my = (y.toLong() * maskHeight / source.height).toInt().coerceAtMost(maskHeight - 1)
                for (x in 0 until source.width) {
                    val mx = (x.toLong() * maskWidth / source.width).toInt().coerceAtMost(maskWidth - 1)
                    val index = my * maskWidth + mx
                    val confidence = maskBuffer.getFloat(index * 4).coerceIn(0f, 1f)
                    // A short feathered transition prevents visibly jagged edges around hair.
                    val alpha = (((confidence - 0.12f) / 0.66f).coerceIn(0f, 1f) * 255f).toInt()
                    val color = source.getPixel(x, y)
                    pixels[y * source.width + x] = (alpha shl 24) or (color and 0x00ffffff)
                }
            }
            return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            }
        } finally {
            segmenter.close()
        }
    }
}
