package com.mhirex.editor.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ScaleGestureDetector
import android.view.View
import com.mhirex.editor.models.EditOperation
import com.mhirex.editor.models.TextPosition
import java.io.File

/**
 * TextOverlayView renders text overlays on top of the video player.
 * This allows users to see text operations in real-time without FFmpeg processing.
 */
class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init { isClickable = true }

    private val paint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private var isDraggingSubtitle = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialRelativeX = 0.5f
    private var initialRelativeY = 0.8f

    private val subtitleBounds = android.graphics.RectF()
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#007AFF") // Active accent color
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    var isSubtitlesEditingActive: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var subtitleOperation: EditOperation.AddSubtitles? = null
        set(value) {
            field = value
            this.subtitleCues = value?.cues ?: emptyList()
            invalidate()
        }
    var onSubtitlePositionChanged: ((relativeX: Float, relativeY: Float) -> Unit)? = null
    var onOperationTapped: ((String) -> Unit)? = null
    private var touchedTextOperationId: String? = null

    private var overlayOperations: List<EditOperation> = emptyList()
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    private fun loadBitmapFromUri(uri: android.net.Uri): Bitmap? {
        val cacheKey = uri.toString()
        if (bitmapCache.containsKey(cacheKey)) {
            return bitmapCache[cacheKey]
        }
        try {
            val bitmap = if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } else {
                val path = uri.path
                if (path != null && File(path).exists()) {
                    BitmapFactory.decodeFile(path)
                } else {
                    null
                }
            }
            if (bitmap != null) {
                bitmapCache[cacheKey] = bitmap
                return bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private var videoWidth = 0
    private var videoHeight = 0
    var currentPositionMs: Long = 0L
        set(value) {
            field = value
            invalidate()
        }
    var hiddenOperationId: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private var subtitleCues: List<com.mhirex.editor.models.SubtitleCue> = emptyList()

    fun setTextOperations(operations: List<EditOperation.AddText>) {
        this.overlayOperations = operations
        invalidate()
    }

    fun setOverlayOperations(operations: List<EditOperation>) {
        this.overlayOperations = operations
        invalidate()
    }

    fun setSubtitleCues(cues: List<com.mhirex.editor.models.SubtitleCue>) {
        this.subtitleCues = cues
        invalidate()
    }

    fun setVideoSize(width: Int, height: Int) {
        this.videoWidth = width
        this.videoHeight = height
        invalidate()
    }

    private fun getVideoRect(): android.graphics.RectF {
        val rect = android.graphics.RectF()
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            return rect
        }

        val containerRatio = width.toFloat() / height
        val videoRatio = videoWidth.toFloat() / videoHeight

        if (videoRatio > containerRatio) {
            val h = width / videoRatio
            val top = (height - h) / 2f
            rect.set(0f, top, width.toFloat(), top + h)
        } else {
            val w = height * videoRatio
            val left = (width - w) / 2f
            rect.set(left, 0f, left + w, height.toFloat())
        }
        return rect
    }

    private fun interpolateKeyframes(
        keyframes: List<EditOperation.KeyframePoint>,
        timeMs: Long,
        defaultValue: Float
    ): Float {
        if (keyframes.isEmpty()) return defaultValue
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) {
            return sorted.first().valueX
        }
        if (timeMs >= sorted.last().timeMs) {
            return sorted.last().valueX
        }
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs >= k1.timeMs && timeMs <= k2.timeMs) {
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs)
                return k1.valueX + progress * (k2.valueX - k1.valueX)
            }
        }
        return defaultValue
    }

    private fun interpolateKeyframePosition(
        keyframes: List<EditOperation.KeyframePoint>,
        timeMs: Long,
        defaultX: Float,
        defaultY: Float
    ): Pair<Float, Float> {
        if (keyframes.isEmpty()) return Pair(defaultX, defaultY)
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) {
            return Pair(sorted.first().valueX, sorted.first().valueY)
        }
        if (timeMs >= sorted.last().timeMs) {
            return Pair(sorted.last().valueX, sorted.last().valueY)
        }
        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeMs >= k1.timeMs && timeMs <= k2.timeMs) {
                val progress = (timeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs)
                val x = k1.valueX + progress * (k2.valueX - k1.valueX)
                val y = k1.valueY + progress * (k2.valueY - k1.valueY)
                return Pair(x, y)
            }
        }
        return Pair(defaultX, defaultY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val videoRect = getVideoRect()
        val scale = if (videoHeight > 0) videoRect.height() / videoHeight.toFloat() else 1f

        for (op in overlayOperations) {
            if (op is EditOperation.AddText) {
                if (op.id == hiddenOperationId) continue
                val start = op.startTimeMs ?: 0L
                val end = op.endTimeMs ?: Long.MAX_VALUE
                if (currentPositionMs < start || currentPositionMs > end) continue

                paint.textSize = op.fontSize * scale
                if (op.fontPath != null) {
                    try {
                        paint.typeface = android.graphics.Typeface.createFromFile(op.fontPath)
                    } catch (e: Exception) {
                        paint.typeface = android.graphics.Typeface.DEFAULT
                    }
                } else {
                    paint.typeface = android.graphics.Typeface.DEFAULT
                }

                val relativeTimeMs = currentPositionMs - start
                val interpolatedPos = if (op.positionKeyframes.isNotEmpty()) {
                    interpolateKeyframePosition(op.positionKeyframes, relativeTimeMs, op.relativeX ?: 0.5f, op.relativeY ?: 0.5f)
                } else {
                    Pair(op.relativeX, op.relativeY)
                }
                val interpolatedOpacity = if (op.opacityKeyframes.isNotEmpty()) {
                    interpolateKeyframes(op.opacityKeyframes, relativeTimeMs, op.opacity)
                } else {
                    op.opacity
                }

                val baseColorInt = try { Color.parseColor(op.color) } catch (e: Exception) { Color.WHITE }
                val alphaInt = (interpolatedOpacity * 255).toInt().coerceIn(0, 255)
                paint.color = Color.argb(alphaInt, Color.red(baseColorInt), Color.green(baseColorInt), Color.blue(baseColorInt))
                
                paint.letterSpacing = op.letterSpacing

                val visibleText = if (op.animationPreset == "Typewriter") {
                    val points = op.text.codePoints().toArray()
                    val duration = ((op.endTimeMs ?: start + 900L) - start).coerceAtLeast(500L)
                    val count = ((relativeTimeMs.toFloat() / duration).coerceIn(0f, 1f) * points.size).toInt().coerceAtMost(points.size)
                    if (count == 0) continue
                    String(points, 0, count)
                } else op.text
                val lines = visibleText.split("\n")
                val textHeight = (paint.descent() - paint.ascent()) + op.lineSpacing * scale
                val totalHeight = lines.size * textHeight
                
                // Find max width among lines
                var maxTextWidth = 0f
                for (line in lines) {
                    val w = paint.measureText(line)
                    if (w > maxTextWidth) maxTextWidth = w
                }

                val startX: Float
                val startY: Float

                val rx = interpolatedPos.first
                val ry = interpolatedPos.second
                val hasCustomPos = rx != null && ry != null || op.positionKeyframes.isNotEmpty()

                if (hasCustomPos) {
                    // WYSIWYG drag-and-drop coordinates (fractional 0.0–1.0) relative to text center
                    val cx = rx ?: 0.5f
                    val cy = ry ?: 0.5f
                    val centerX = videoRect.left + (cx * videoRect.width())
                    val centerY = videoRect.top + (cy * videoRect.height())
                    startX = centerX - (maxTextWidth / 2f)
                    startY = centerY - (totalHeight / 2f) + (textHeight / 2f) - ((paint.ascent() + paint.descent()) / 2f)
                } else {
                    val rectW = videoRect.width()
                    val rectH = videoRect.height()
                    val rectL = videoRect.left
                    val rectT = videoRect.top

                    when (op.position) {
                        TextPosition.TOP_LEFT -> {
                            startX = rectL + 16f
                            startY = rectT + 32f - paint.ascent()
                        }
                        TextPosition.TOP_CENTER -> {
                            startX = rectL + (rectW - maxTextWidth) / 2
                            startY = rectT + 32f - paint.ascent()
                        }
                        TextPosition.TOP_RIGHT -> {
                            startX = rectL + rectW - maxTextWidth - 16f
                            startY = rectT + 32f - paint.ascent()
                        }
                        TextPosition.CENTER_LEFT -> {
                            startX = rectL + 16f
                            startY = rectT + rectH / 2 - totalHeight / 2 - paint.ascent()
                        }
                        TextPosition.CENTER -> {
                            startX = rectL + (rectW - maxTextWidth) / 2
                            startY = rectT + rectH / 2 - totalHeight / 2 - paint.ascent()
                        }
                        TextPosition.CENTER_RIGHT -> {
                            startX = rectL + rectW - maxTextWidth - 16f
                            startY = rectT + rectH / 2 - totalHeight / 2 - paint.ascent()
                        }
                        TextPosition.BOTTOM_LEFT -> {
                            startX = rectL + 16f
                            startY = rectT + rectH - 16f - totalHeight + textHeight - paint.descent()
                        }
                        TextPosition.BOTTOM_CENTER -> {
                            startX = rectL + (rectW - maxTextWidth) / 2
                            startY = rectT + rectH - 16f - totalHeight + textHeight - paint.descent()
                        }
                        TextPosition.BOTTOM_RIGHT -> {
                            startX = rectL + rectW - maxTextWidth - 16f
                            startY = rectT + rectH - 16f - totalHeight + textHeight - paint.descent()
                        }
                        TextPosition.CENTER_BOTTOM -> {
                            startX = rectL + (rectW - maxTextWidth) / 2
                            startY = rectT + rectH - 16f - totalHeight + textHeight - paint.descent()
                        }
                        TextPosition.CENTER_TOP -> {
                            startX = rectL + (rectW - maxTextWidth) / 2
                            startY = rectT + 32f - paint.ascent()
                        }
                    }
                }

                val animatedScale = if (op.scaleKeyframes.isNotEmpty()) {
                    interpolateKeyframes(op.scaleKeyframes, relativeTimeMs, 1f).coerceAtLeast(0.05f)
                } else 1f
                canvas.save()
                canvas.scale(animatedScale, animatedScale, startX + maxTextWidth / 2f, startY)
                if (op.animationPreset == "Blur") {
                    paint.setShadowLayer(7f * scale, 0f, 0f, baseColorInt)
                } else {
                    paint.clearShadowLayer()
                }

                // Draw each line
                for ((index, line) in lines.withIndex()) {
                    val lineW = paint.measureText(line)
                    val lineX = when (op.textAlign) {
                        "left", "L" -> startX
                        "right", "R" -> startX + maxTextWidth - lineW
                        else -> startX + (maxTextWidth - lineW) / 2 // center
                    }
                    val lineY = startY + (index * textHeight)

                    if (op.borderThickness > 0) {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = op.borderThickness * scale
                        paint.strokeJoin = Paint.Join.ROUND
                        val borderColorInt = try { Color.parseColor(op.borderColor) } catch (e: Exception) { Color.BLACK }
                        paint.color = Color.argb(alphaInt, Color.red(borderColorInt), Color.green(borderColorInt), Color.blue(borderColorInt))
                        canvas.drawText(line, lineX, lineY, paint)

                        paint.style = Paint.Style.FILL
                        paint.color = Color.argb(alphaInt, Color.red(baseColorInt), Color.green(baseColorInt), Color.blue(baseColorInt))
                    } else {
                        paint.style = Paint.Style.FILL
                    }
                    
                    canvas.drawText(line, lineX, lineY, paint)
                }
                canvas.restore()
                paint.clearShadowLayer()
            }
        }

        // Render subtitles centered at bottom or custom positioned
        val activeCue = subtitleCues.firstOrNull { currentPositionMs in it.startTimeMs..it.endTimeMs }
            ?: if (isSubtitlesEditingActive && subtitleOperation != null) {
                com.mhirex.editor.models.SubtitleCue(0, 0, "[Subtitle Preview]")
            } else {
                null
            }

        if (activeCue != null) {
            val subOp = subtitleOperation
            val fontSizeVal = subOp?.fontSize ?: 22
            paint.textSize = fontSizeVal.toFloat() * context.resources.displayMetrics.density * scale
            if (subOp?.fontPath != null) {
                try {
                    paint.typeface = android.graphics.Typeface.createFromFile(subOp.fontPath)
                } catch (e: Exception) {
                    paint.typeface = android.graphics.Typeface.DEFAULT
                }
            } else {
                paint.typeface = android.graphics.Typeface.DEFAULT
            }
            paint.color = Color.WHITE

            val bgPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = Color.parseColor("#80000000")
            }
            val showBg = true

            val rectW = videoRect.width()
            val rectH = videoRect.height()
            val rectL = videoRect.left
            val rectT = videoRect.top

            val maxAvailableWidth = rectW * 0.85f
            val lines = wrapTextForCanvas(activeCue.text, paint, maxAvailableWidth)
            val textHeight = paint.descent() - paint.ascent()
            
            val totalBlockHeight = lines.size * textHeight
            val maxLineWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f

            val refX: Float
            val refY: Float

            if (subOp != null && subOp.hasCustomPosition()) {
                refX = rectL + (subOp.relativeX!! * rectW)
                refY = rectT + (subOp.relativeY!! * rectH)
                
                // Draw centering each line horizontally around refX and the whole block vertically around refY
                var currentY = refY - (totalBlockHeight / 2f) - ((paint.ascent() + paint.descent()) / 2f)
                
                // Update bounds for border
                val paddingVal = 12f * scale
                subtitleBounds.set(
                    refX - (maxLineWidth / 2f) - paddingVal,
                    refY - (totalBlockHeight / 2f) - paddingVal,
                    refX + (maxLineWidth / 2f) + paddingVal,
                    refY + (totalBlockHeight / 2f) + paddingVal
                )

                for (line in lines) {
                    val lineTextWidth = paint.measureText(line)
                    val lineX = refX - (lineTextWidth / 2f)
                    
                    if (showBg) {
                        val paddingX = 8f * scale
                        val paddingY = 4f * scale
                        canvas.drawRoundRect(
                            lineX - paddingX,
                            currentY + paint.ascent() - paddingY,
                            lineX + lineTextWidth + paddingX,
                            currentY + paint.descent() + paddingY,
                            8f * scale, 8f * scale, bgPaint
                        )
                    }
                    canvas.drawText(line, lineX, currentY, paint)
                    currentY += textHeight
                }
            } else {
                // Predefined position alignment
                val pos = subOp?.position ?: TextPosition.BOTTOM_CENTER
                
                when (pos) {
                    TextPosition.TOP_LEFT -> {
                        refX = rectL + 16f * scale + (maxLineWidth / 2f)
                        refY = rectT + 32f * scale + (totalBlockHeight / 2f)
                    }
                    TextPosition.TOP_CENTER, TextPosition.CENTER_TOP -> {
                        refX = rectL + rectW / 2
                        refY = rectT + 32f * scale + (totalBlockHeight / 2f)
                    }
                    TextPosition.TOP_RIGHT -> {
                        refX = rectL + rectW - 16f * scale - (maxLineWidth / 2f)
                        refY = rectT + 32f * scale + (totalBlockHeight / 2f)
                    }
                    TextPosition.CENTER_LEFT -> {
                        refX = rectL + 16f * scale + (maxLineWidth / 2f)
                        refY = rectT + rectH / 2
                    }
                    TextPosition.CENTER -> {
                        refX = rectL + rectW / 2
                        refY = rectT + rectH / 2
                    }
                    TextPosition.CENTER_RIGHT -> {
                        refX = rectL + rectW - 16f * scale - (maxLineWidth / 2f)
                        refY = rectT + rectH / 2
                    }
                    TextPosition.BOTTOM_LEFT -> {
                        refX = rectL + 16f * scale + (maxLineWidth / 2f)
                        refY = rectT + rectH - 16f * scale - (totalBlockHeight / 2f)
                    }
                    TextPosition.BOTTOM_CENTER, TextPosition.CENTER_BOTTOM -> {
                        refX = rectL + rectW / 2
                        val density = context.resources.displayMetrics.density
                        refY = rectT + rectH - 24f * scale * density - (totalBlockHeight / 2f)
                    }
                    TextPosition.BOTTOM_RIGHT -> {
                        refX = rectL + rectW - 16f * scale - (maxLineWidth / 2f)
                        refY = rectT + rectH - 16f * scale - (totalBlockHeight / 2f)
                    }
                }
                
                // Update bounds for border
                val paddingVal = 12f * scale
                subtitleBounds.set(
                    refX - (maxLineWidth / 2f) - paddingVal,
                    refY - (totalBlockHeight / 2f) - paddingVal,
                    refX + (maxLineWidth / 2f) + paddingVal,
                    refY + (totalBlockHeight / 2f) + paddingVal
                )

                var currentY = refY - (totalBlockHeight / 2f) - ((paint.ascent() + paint.descent()) / 2f)
                for (line in lines) {
                    val lineTextWidth = paint.measureText(line)
                    val lineX = when (pos) {
                        TextPosition.TOP_LEFT, TextPosition.CENTER_LEFT, TextPosition.BOTTOM_LEFT -> {
                            refX - (maxLineWidth / 2f)
                        }
                        TextPosition.TOP_RIGHT, TextPosition.CENTER_RIGHT, TextPosition.BOTTOM_RIGHT -> {
                            refX + (maxLineWidth / 2f) - lineTextWidth
                        }
                        else -> {
                            refX - (lineTextWidth / 2f)
                        }
                    }
                    
                    if (showBg) {
                        val paddingX = 8f * scale
                        val paddingY = 4f * scale
                        canvas.drawRoundRect(
                            lineX - paddingX,
                            currentY + paint.ascent() - paddingY,
                            lineX + lineTextWidth + paddingX,
                            currentY + paint.descent() + paddingY,
                            8f * scale, 8f * scale, bgPaint
                        )
                    }
                    canvas.drawText(line, lineX, currentY, paint)
                    currentY += textHeight
                }
            }

            // Draw dotted selection border if subtitles editing is active
            if (isSubtitlesEditingActive) {
                canvas.drawRect(subtitleBounds, selectionPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (!isSubtitlesEditingActive || subtitleOperation == null) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchedTextOperationId = findTextOperationAt(event.x, event.y)
                    return touchedTextOperationId != null
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val id = touchedTextOperationId
                    touchedTextOperationId = null
                    if (id != null) {
                        performClick()
                        onOperationTapped?.invoke(id)
                        return true
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> touchedTextOperationId = null
                else -> if (touchedTextOperationId != null) return true
            }
            return false
        }


        val videoRect = getVideoRect()

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y
                if (subtitleBounds.contains(touchX, touchY)) {
                    isDraggingSubtitle = true
                    dragStartX = touchX
                    dragStartY = touchY
                    initialRelativeX = subtitleOperation?.relativeX ?: 0.5f
                    initialRelativeY = subtitleOperation?.relativeY ?: 0.8f
                    return true
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isDraggingSubtitle) {
                    val deltaX = event.x - dragStartX
                    val deltaY = event.y - dragStartY
                    val newRelX = (initialRelativeX + deltaX / videoRect.width()).coerceIn(0f, 1f)
                    val newRelY = (initialRelativeY + deltaY / videoRect.height()).coerceIn(0f, 1f)
                    onSubtitlePositionChanged?.invoke(newRelX, newRelY)
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isDraggingSubtitle) {
                    isDraggingSubtitle = false
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun findTextOperationAt(touchX: Float, touchY: Float): String? {
        val videoRect = getVideoRect()
        val scale = if (videoHeight > 0) videoRect.height() / videoHeight.toFloat() else 1f
        for (op in overlayOperations.asReversed().filterIsInstance<EditOperation.AddText>()) {
            val start = op.startTimeMs ?: 0L
            if (op.id == hiddenOperationId || currentPositionMs < start || currentPositionMs > (op.endTimeMs ?: Long.MAX_VALUE)) continue
            paint.textSize = op.fontSize * scale
            paint.typeface = try {
                op.fontPath?.let { android.graphics.Typeface.createFromFile(it) } ?: android.graphics.Typeface.DEFAULT
            } catch (_: Exception) { android.graphics.Typeface.DEFAULT }
            val lines = op.text.split("\n")
            val textWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
            val lineHeight = paint.descent() - paint.ascent() + op.lineSpacing * scale
            val textHeight = lineHeight * lines.size
            val relativeTimeMs = currentPositionMs - start
            val position = if (op.positionKeyframes.isNotEmpty()) {
                interpolateKeyframePosition(op.positionKeyframes, relativeTimeMs, op.relativeX ?: 0.5f, op.relativeY ?: 0.5f)
            } else op.relativeX to op.relativeY
            val centerX = videoRect.left + (position.first ?: 0.5f) * videoRect.width()
            val centerY = videoRect.top + (position.second ?: 0.5f) * videoRect.height()
            val padding = 24f * resources.displayMetrics.density
            if (touchX >= centerX - textWidth / 2f - padding && touchX <= centerX + textWidth / 2f + padding &&
                touchY >= centerY - textHeight / 2f - padding && touchY <= centerY + textHeight / 2f + padding
            ) return op.id
        }
        return null
    }

    private fun wrapTextForCanvas(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (maxWidth <= 0f) return listOf(text)
        val resultLines = mutableListOf<String>()
        val rawLines = text.split("\n")
        for (rawLine in rawLines) {
            if (paint.measureText(rawLine) <= maxWidth) {
                resultLines.add(rawLine)
            } else {
                val words = rawLine.split(Regex("\\s+"))
                var currentLine = StringBuilder()
                for (word in words) {
                    if (currentLine.isEmpty()) {
                        currentLine.append(word)
                    } else {
                        val testLine = "$currentLine $word"
                        if (paint.measureText(testLine) <= maxWidth) {
                            currentLine.append(" ").append(word)
                        } else {
                            resultLines.add(currentLine.toString())
                            currentLine = StringBuilder(word)
                        }
                    }
                }
                if (currentLine.isNotEmpty()) {
                    resultLines.add(currentLine.toString())
                }
            }
        }
        return if (resultLines.isEmpty()) listOf(text) else resultLines
    }
}
