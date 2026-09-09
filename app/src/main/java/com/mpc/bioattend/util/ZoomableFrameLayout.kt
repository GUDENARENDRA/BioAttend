package com.mpc.bioattend.util

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

class ZoomableFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var scaleFactor = 1.0f
        private set

    var onScaleChangedListener: ((Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 2.5f)

            pivotX = detector.focusX
            pivotY = detector.focusY
            scaleX = scaleFactor
            scaleY = scaleFactor

            onScaleChangedListener?.invoke(scaleFactor)
            return true
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.pointerCount >= 2) {
            scaleDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        pivotX = 0f
        pivotY = 0f
        scaleX = 1.0f
        scaleY = 1.0f
        onScaleChangedListener?.invoke(1.0f)
    }
}
