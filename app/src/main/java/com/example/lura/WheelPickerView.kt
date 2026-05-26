package com.example.lura

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class WheelPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minValue: Int = 0
        private set
    var maxValue: Int = 0
        private set
    var wrapSelectorWheel: Boolean = true

    var displayedValues: Array<String>? = null
        set(value) {
            field = value
            invalidate()
        }

    var selectedTextSizeSp: Float = DEFAULT_SELECTED_TEXT_SIZE_SP
        set(value) {
            field = value
            invalidate()
        }

    var secondaryTextSizeSp: Float = DEFAULT_SECONDARY_TEXT_SIZE_SP
        set(value) {
            field = value
            invalidate()
        }

    var value: Int
        get() = selectedValue
        set(newValue) {
            setValueInternal(newValue, notify = false)
        }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lura_time_picker_text)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.lura_text_secondary)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private var listener: ((oldValue: Int, newValue: Int) -> Unit)? = null
    private var selectedValue = 0
    private var scrollOffsetPx = 0f
    private var lastTouchY = 0f
    private var activeAnimator: ValueAnimator? = null

    private val itemSpacingPx = 82f.dpToPx()

    init {
        isClickable = true
        minValue = 0
        maxValue = 0
        selectedValue = 0
    }

    fun setRange(
        minValue: Int,
        maxValue: Int,
        displayedValues: Array<String>? = null,
        wrapSelectorWheel: Boolean = true
    ) {
        require(maxValue >= minValue) { "maxValue must be greater than or equal to minValue" }
        if (displayedValues != null) {
            require(displayedValues.size == (maxValue - minValue + 1)) {
                "displayedValues size must match range"
            }
        }

        this.minValue = minValue
        this.maxValue = maxValue
        this.displayedValues = displayedValues
        this.wrapSelectorWheel = wrapSelectorWheel
        setValueInternal(value.coerceIn(minValue, maxValue), notify = false)
        invalidate()
    }

    fun setOnValueChangedListener(listener: ((oldValue: Int, newValue: Int) -> Unit)?) {
        this.listener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        drawCenterGuide(canvas, centerX, centerY)
        val nearestToCenter = (-scrollOffsetPx / itemSpacingPx).roundToInt().coerceIn(-2, 2)

        for (relativeIndex in -2..2) {
            val itemValue = valueForOffset(relativeIndex) ?: continue
            val y = centerY + (relativeIndex * itemSpacingPx) + scrollOffsetPx
            val distanceRatio = (abs(y - centerY) / itemSpacingPx).coerceIn(0f, 1f)
            val isNearestToCenter = relativeIndex == nearestToCenter
            val paint = if (isNearestToCenter) selectedPaint else secondaryPaint
            val textSize = if (isNearestToCenter) {
                selectedTextSizeSp.spToPx()
            } else {
                secondaryTextSizeSp.spToPx()
            }

            paint.textSize = textSize
            paint.alpha = if (isNearestToCenter) {
                255
            } else {
                ((1f - (distanceRatio * 0.6f)) * 170).roundToInt().coerceIn(55, 150)
            }

            val baseline = y - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(labelFor(itemValue), centerX, baseline, paint)
        }

        selectedPaint.alpha = 255
        secondaryPaint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activeAnimator?.cancel()
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - lastTouchY
                lastTouchY = event.y
                scrollOffsetPx += deltaY
                constrainOffsetAtEdges()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                snapToNearestValue()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawCenterGuide(canvas: Canvas, centerX: Float, centerY: Float) {
        val guidePaint = secondaryPaint
        guidePaint.alpha = 90
        guidePaint.strokeWidth = 1.5f.dpToPx()
        val halfWidth = (width * 0.33f).coerceAtMost(120f.dpToPx())
        canvas.drawLine(centerX - halfWidth, centerY - 35f.dpToPx(), centerX + halfWidth, centerY - 35f.dpToPx(), guidePaint)
        canvas.drawLine(centerX - halfWidth, centerY + 35f.dpToPx(), centerX + halfWidth, centerY + 35f.dpToPx(), guidePaint)
        guidePaint.alpha = 255
    }

    private fun snapToNearestValue() {
        val steps = (-scrollOffsetPx / itemSpacingPx).roundToInt()
        val oldValue = value
        val newValue = normalizedValue(value + steps)

        val appliedSteps = distanceInSteps(oldValue, newValue, steps)
        scrollOffsetPx += appliedSteps * itemSpacingPx
        setValueInternal(newValue, notify = true)
        animateOffsetToCenter()
    }

    private fun animateOffsetToCenter() {
        activeAnimator?.cancel()
        val startOffset = scrollOffsetPx
        activeAnimator = ValueAnimator.ofFloat(startOffset, 0f).apply {
            duration = 140L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                scrollOffsetPx = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun constrainOffsetAtEdges() {
        if (wrapSelectorWheel) return
        val maxDrag = itemSpacingPx * 0.45f
        if (value == minValue && scrollOffsetPx > maxDrag) {
            scrollOffsetPx = maxDrag
        }
        if (value == maxValue && scrollOffsetPx < -maxDrag) {
            scrollOffsetPx = -maxDrag
        }
    }

    private fun setValueInternal(newValue: Int, notify: Boolean) {
        val normalized = normalizedValue(newValue)
        val oldValue = selectedValue
        selectedValue = normalized
        if (notify && oldValue != normalized) {
            listener?.invoke(oldValue, normalized)
        }
        invalidate()
    }

    private fun normalizedValue(candidate: Int): Int {
        if (wrapSelectorWheel) {
            val count = max(1, maxValue - minValue + 1)
            val normalizedOffset = Math.floorMod(candidate - minValue, count)
            return minValue + normalizedOffset
        }
        return candidate.coerceIn(minValue, maxValue)
    }

    private fun valueForOffset(offset: Int): Int? {
        val candidate = value + offset
        if (wrapSelectorWheel) return normalizedValue(candidate)
        return candidate.takeIf { it in minValue..maxValue }
    }

    private fun distanceInSteps(oldValue: Int, newValue: Int, requestedSteps: Int): Int {
        if (!wrapSelectorWheel) {
            return newValue - oldValue
        }
        return requestedSteps
    }

    private fun labelFor(value: Int): String {
        val labels = displayedValues
        return labels?.get(value - minValue) ?: value.toString()
    }

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun Float.spToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)

    private companion object {
        const val DEFAULT_SELECTED_TEXT_SIZE_SP = 52f
        const val DEFAULT_SECONDARY_TEXT_SIZE_SP = 22f
    }
}
