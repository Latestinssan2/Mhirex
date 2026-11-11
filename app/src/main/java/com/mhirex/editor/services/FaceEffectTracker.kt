package com.mhirex.editor.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.mhirex.editor.models.EditOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.hypot

/** Sparse, on-device face tracking points used to animate accessory overlays. */
data class FaceEffectTrack(
    val positions: List<EditOperation.KeyframePoint>,
    val scales: List<EditOperation.KeyframePoint>,
    val rotations: List<EditOperation.KeyframePoint>,
    val baseWidthFraction: Float
)

object FaceEffectTracker {
    enum class Accessory { GOGGLES, SKULL_MASK }

    suspend fun track(
        context: Context,
        uri: Uri,
        trimStartMs: Long,
        speed: Float,
        durationMs: Long,
        accessory: Accessory,
    ): FaceEffectTrack? = withContext(Dispatchers.IO) {
        if (durationMs <= 0L) return@withContext null
        val retriever = MediaMetadataRetriever()
        var landmarker: FaceLandmarker? = null
        try {
            retriever.setDataSource(context, uri)
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .build()
            landmarker = FaceLandmarker.createFromOptions(context, options)

            // Bound work for long source clips while keeping enough samples for a smooth edit.
            val stepMs = maxOf(400L, durationMs / 79L)
            val raw = mutableListOf<RawPoint>()
            var t = 0L
            while (t <= durationMs && raw.size < 80) {
                val sourceMs = trimStartMs + (t * speed).toLong()
                val bitmap = retriever.getFrameAtTime(sourceMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    try {
                        val faces = landmarker.detect(BitmapImageBuilder(bitmap).build()).faceLandmarks()
                        val face = faces.firstOrNull()
                        if (face != null && face.size > 454) {
                            val (left, right, centerY) = when (accessory) {
                                Accessory.GOGGLES -> {
                                    val l = face[33]
                                    val r = face[263]
                                    Triple(l, r, (l.y() + r.y()) / 2f)
                                }
                                Accessory.SKULL_MASK -> {
                                    val l = face[234]
                                    val r = face[454]
                                    val nose = face[1]
                                    val chin = face[152]
                                    Triple(l, r, (nose.y() + chin.y()) / 2f)
                                }
                            }
                            val faceWidth = hypot(right.x() - left.x(), right.y() - left.y()).coerceIn(0.08f, 0.85f)
                            val centerX = ((left.x() + right.x()) / 2f).coerceIn(0.02f, 0.98f)
                            val safeY = centerY.coerceIn(0.02f, 0.98f)
                            val angle = Math.toDegrees(atan2((right.y() - left.y()).toDouble(), (right.x() - left.x()).toDouble()))
                                .toFloat().coerceIn(-45f, 45f)
                            raw += RawPoint(t, centerX, safeY, faceWidth, angle)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                t += stepMs
            }
            val first = raw.firstOrNull() ?: return@withContext null
            val positions = raw.map { EditOperation.KeyframePoint(it.timeMs, it.x, it.y) }
            val scales = raw.map {
                EditOperation.KeyframePoint(it.timeMs, (it.width / first.width).coerceIn(0.55f, 1.8f))
            }
            val rotations = raw.map { EditOperation.KeyframePoint(it.timeMs, it.angle) }
            FaceEffectTrack(positions, scales, rotations, first.width)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { landmarker?.close() }
            runCatching { retriever.release() }
        }
    }

    private data class RawPoint(val timeMs: Long, val x: Float, val y: Float, val width: Float, val angle: Float)
}
