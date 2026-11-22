package com.example.verbolandia.crossword

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.verbolandia.R

class CrosswordGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCellClickListener {
        fun onCellClicked(row: Int, col: Int)
    }

    var listener: OnCellClickListener? = null

    private var board: CrosswordBoard? = null
    private var activeWord: CrosswordWord? = null
    private var cursorRow: Int = -1
    private var cursorCol: Int = -1

    // Theme Colors
    private val colorPrimary = ContextCompat.getColor(context, R.color.primary)
    private val colorOnSurface = ContextCompat.getColor(context, R.color.on_surface)
    private val colorOnSurfaceVariant = ContextCompat.getColor(context, R.color.on_surface_variant)
    private val colorSurfaceLowest = ContextCompat.getColor(context, R.color.surface_container_lowest)
    private val colorOutlineVariant = ContextCompat.getColor(context, R.color.outline_variant)
    private val colorSuccess = ContextCompat.getColor(context, R.color.success)

    // Highlight Tints
    private val colorWordHighlight = (colorPrimary and 0x00FFFFFF) or 0x26000000 // 15% opacity primary
    private val colorSolvedHighlight = (colorSuccess and 0x00FFFFFF) or 0x22000000 // 13% opacity success
    private val colorInactiveCell = (colorOutlineVariant and 0x00FFFFFF) or 0x1A000000 // subtle

    // Paints
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val cursorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = colorPrimary
    }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorOnSurface
        isFakeBoldText = true
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        color = colorOnSurfaceVariant
        isFakeBoldText = true
    }

    private val cellRect = RectF()
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setBoard(crosswordBoard: CrosswordBoard) {
        this.board = crosswordBoard
        requestLayout()
        invalidate()
    }

    fun setActiveWord(word: CrosswordWord?) {
        this.activeWord = word
        invalidate()
    }

    fun setCursor(row: Int, col: Int) {
        this.cursorRow = row
        this.cursorCol = col
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (height in 1 until width) height else width
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boardSize = board?.size ?: CrosswordGenerator.DEFAULT_GRID_SIZE
        val minDim = minOf(w, h).toFloat()
        cellSize = minDim / boardSize
        offsetX = (w - minDim) / 2f
        offsetY = (h - minDim) / 2f

        letterPaint.textSize = cellSize * 0.52f
        numberPaint.textSize = cellSize * 0.26f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = board ?: return
        val size = b.size
        val density = resources.displayMetrics.density
        val cornerRadius = 4f * density

        for (r in 0 until size) {
            for (c in 0 until size) {
                val left = offsetX + c * cellSize
                val top = offsetY + r * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                val pad = 1f * density
                cellRect.set(left + pad, top + pad, right - pad, bottom - pad)

                val cell = b.getCell(r, c)
                if (cell == null) {
                    // Inactive / block cell
                    fillPaint.color = colorInactiveCell
                    canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, fillPaint)
                } else {
                    // Active crossword cell
                    val isCursor = (r == cursorRow && c == cursorCol)
                    val isInActiveWord = activeWord?.let { w ->
                        cell.wordNumbers.contains(w.number)
                    } ?: false
                    val isSolvedCell = b.words.any { w -> w.isSolved && cell.wordNumbers.contains(w.number) }

                    // Determine fill color
                    fillPaint.color = when {
                        isSolvedCell -> colorSolvedHighlight
                        isInActiveWord -> colorWordHighlight
                        else -> colorSurfaceLowest
                    }
                    canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, fillPaint)

                    // Determine border stroke
                    if (isCursor) {
                        canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cursorStrokePaint)
                    } else {
                        strokePaint.color = if (isInActiveWord) colorPrimary else colorOutlineVariant
                        canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, strokePaint)
                    }

                    // Draw start cell number
                    if (cell.isStartingCell && cell.cellNumber > 0) {
                        val numX = cellRect.left + 3f * density
                        val numY = cellRect.top + numberPaint.textSize + 1f * density
                        canvas.drawText(cell.cellNumber.toString(), numX, numY, numberPaint)
                    }

                    // Draw entered character
                    if (cell.isFilled) {
                        val textX = cellRect.centerX()
                        val textY = cellRect.centerY() - ((letterPaint.descent() + letterPaint.ascent()) / 2f)
                        letterPaint.color = if (isSolvedCell) colorSuccess else colorOnSurface
                        canvas.drawText(cell.enteredChar.toString(), textX, textY, letterPaint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val b = board ?: return false
            val touchX = event.x - offsetX
            val touchY = event.y - offsetY
            if (touchX in 0f..(b.size * cellSize) && touchY in 0f..(b.size * cellSize)) {
                val col = (touchX / cellSize).toInt().coerceIn(0, b.size - 1)
                val row = (touchY / cellSize).toInt().coerceIn(0, b.size - 1)
                if (b.getCell(row, col) != null) {
                    listener?.onCellClicked(row, col)
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}
