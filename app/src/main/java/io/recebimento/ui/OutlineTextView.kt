package io.recebimento.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

class OutlineTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var outlineColor: Int = 0xFF4CAF50.toInt()
    var outlineWidth: Float = 0.5f * resources.displayMetrics.density

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val textLayout = layout ?: return
        strokePaint.color = outlineColor
        strokePaint.strokeWidth = outlineWidth
        strokePaint.textSize = textSize
        strokePaint.typeface = typeface
        strokePaint.textSkewX = paint.textSkewX
        canvas.save()
        canvas.translate(
            (compoundPaddingLeft + scrollX).toFloat(),
            (compoundPaddingTop + scrollY).toFloat()
        )
        for (i in 0 until textLayout.lineCount) {
            canvas.drawText(
                textLayout.text,
                textLayout.getLineStart(i),
                textLayout.getLineEnd(i),
                textLayout.getLineLeft(i),
                textLayout.getLineBaseline(i).toFloat(),
                strokePaint
            )
        }
        canvas.restore()
        super.onDraw(canvas)
    }
}
