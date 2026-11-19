package com.example.syona.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.syona.R

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0f
    private var maxProgress = 100f
    private var centerText = ""
    private var progressText = ""

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.light_gray)
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_blue_green)
        style = Paint.Style.STROKE
        strokeWidth = 20f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_primary)
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }

    private val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    private val rectF = RectF()

    // Geometry is expressed as fractions of the view size; the original design used a fixed
    // 300 px canvas (stroke 20, text 48/24, sub-text offset 40, inset 30), so proportions match.
    private var inset = 30f
    private var subtextOffset = 40f

    fun setProgress(current: Int, max: Int) {
        this.progress = if (max > 0) (current.toFloat() / max.toFloat()) * 100f else 0f
        this.maxProgress = 100f
        this.centerText = "${current}ml"
        this.progressText = "of ${max}ml"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(centerX, centerY) - inset

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Draw background circle
        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)

        // Draw progress arc
        val sweepAngle = (progress / maxProgress) * 360f
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

        // Draw center text
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(centerText, centerX, textY, textPaint)

        // Draw progress text below
        val subtextY = textY + subtextOffset
        canvas.drawText(progressText, centerX, subtextY, subtextPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Previously a hard-coded 300 px, which ignored layout_width/height (260dp in the layout)
        // and rendered much smaller than intended on high-density screens.
        val defaultSize = (DEFAULT_SIZE_DP * resources.displayMetrics.density).toInt()
        val size = minOf(resolveSize(defaultSize, widthMeasureSpec), resolveSize(defaultSize, heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val scale = minOf(w, h) / DESIGN_SIZE_PX
        backgroundPaint.strokeWidth = 20f * scale
        progressPaint.strokeWidth = 20f * scale
        textPaint.textSize = 48f * scale
        subtextPaint.textSize = 24f * scale
        inset = 30f * scale
        subtextOffset = 40f * scale
    }

    private companion object {
        const val DEFAULT_SIZE_DP = 260
        const val DESIGN_SIZE_PX = 300f
    }
}