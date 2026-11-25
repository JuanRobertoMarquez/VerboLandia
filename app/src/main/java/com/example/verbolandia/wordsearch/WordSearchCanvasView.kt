package com.example.verbolandia.wordsearch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.verbolandia.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

class WordSearchCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnWordSearchInteractionListener {
        fun onWordTracing(tracedWord: String)
        fun onWordFound(targetWord: WordSearchTargetWord)
        fun onTracingCancelled()
    }

    var listener: OnWordSearchInteractionListener? = null

    private var board: WordSearchBoard? = null

    // Colors
    private val colorPrimary = ContextCompat.getColor(context, R.color.primary)
    private val colorSuccess = ContextCompat.getColor(context, R.color.success)
    private val colorOnSurface = ContextCompat.getColor(context, R.color.on_surface)
    private val colorSurfaceLowest = ContextCompat.getColor(context, R.color.surface_container_lowest)
    private val colorOutlineVariant = ContextCompat.getColor(context, R.color.outline_variant)

    // Capsule Colors
    private val colorTracingCapsule = (colorPrimary and 0x00FFFFFF) or 0x4D000000 // ~30% primary
    private val colorFoundCapsule = (colorSuccess and 0x00FFFFFF) or 0x3D000000 // ~24% success

    // Paints
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorSurfaceLowest
    }
    private val cellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = colorOutlineVariant
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorOnSurface
        isFakeBoldText = true
    }
    private val capsulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = colorPrimary
    }

    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private val cellRect = RectF()

    // Drag State
    private var isDragging = false
    private var startRow = -1
    private var startCol = -1
    private val currentTracedCells = mutableListOf<Pair<Int, Int>>()

    // Solved paths: list of (startCell, endCell)
    private val solvedPaths = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()

    // Hint cell
    private var hintCell: Pair<Int, Int>? = null

    fun setBoard(wordSearchBoard: WordSearchBoard) {
        this.board = wordSearchBoard
        this.solvedPaths.clear()
        this.currentTracedCells.clear()
        this.hintCell = null

        // If any target word already marked found, add to solvedPaths
        for (w in wordSearchBoard.targetWords) {
            if (w.isFound) {
                val cells = w.getCells()
                if (cells.isNotEmpty()) {
                    solvedPaths.add(Pair(cells.first(), cells.last()))
                }
            }
        }
        requestLayout()
        invalidate()
    }

    fun showHintForWord(target: WordSearchTargetWord) {
        hintCell = Pair(target.startRow, target.startCol)
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
        val boardSize = board?.size ?: WordSearchGenerator.DEFAULT_GRID_SIZE
        val minDim = minOf(w, h).toFloat()
        cellSize = minDim / boardSize
        offsetX = (w - minDim) / 2f
        offsetY = (h - minDim) / 2f
        textPaint.textSize = cellSize * 0.48f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = board ?: return
        val size = b.size
        val density = resources.displayMetrics.density
        val cornerRadius = 6f * density

        // 1. Draw Cell Backgrounds and Letters
        for (r in 0 until size) {
            for (c in 0 until size) {
                val left = offsetX + c * cellSize
                val top = offsetY + r * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                val pad = 1f * density
                cellRect.set(left + pad, top + pad, right - pad, bottom - pad)

                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cellBgPaint)
                canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, cellBorderPaint)

                val char = b.grid[r][c]
                val textX = cellRect.centerX()
                val textY = cellRect.centerY() - ((textPaint.descent() + textPaint.ascent()) / 2f)
                canvas.drawText(char.toString(), textX, textY, textPaint)
            }
        }

        // 2. Draw Solved Word Capsules
        capsulePaint.color = colorFoundCapsule
        capsulePaint.strokeWidth = cellSize * 0.76f
        for ((start, end) in solvedPaths) {
            drawCapsuleLine(canvas, start.first, start.second, end.first, end.second)
        }

        // 3. Draw Active Tracing Capsule
        if (isDragging && currentTracedCells.isNotEmpty()) {
            capsulePaint.color = colorTracingCapsule
            capsulePaint.strokeWidth = cellSize * 0.76f
            val first = currentTracedCells.first()
            val last = currentTracedCells.last()
            drawCapsuleLine(canvas, first.first, first.second, last.first, last.second)
        }

        // 4. Draw Hint Cell Indicator if active
        hintCell?.let { (hr, hc) ->
            val left = offsetX + hc * cellSize + 2f * density
            val top = offsetY + hr * cellSize + 2f * density
            val right = left + cellSize - 4f * density
            val bottom = top + cellSize - 4f * density
            cellRect.set(left, top, right, bottom)
            canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, hintPaint)
        }
    }

    private fun drawCapsuleLine(canvas: Canvas, r1: Int, c1: Int, r2: Int, c2: Int) {
        val x1 = offsetX + c1 * cellSize + cellSize / 2f
        val y1 = offsetY + r1 * cellSize + cellSize / 2f
        val x2 = offsetX + c2 * cellSize + cellSize / 2f
        val y2 = offsetY + r2 * cellSize + cellSize / 2f

        if (r1 == r2 && c1 == c2) {
            // Single cell capsule
            canvas.drawCircle(x1, y1, cellSize * 0.38f, capsulePaint)
        } else {
            canvas.drawLine(x1, y1, x2, y2, capsulePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val b = board ?: return false
        val touchX = event.x - offsetX
        val touchY = event.y - offsetY

        val col = (touchX / cellSize).toInt()
        val row = (touchY / cellSize).toInt()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (row in 0 until b.size && col in 0 until b.size) {
                    isDragging = true
                    startRow = row
                    startCol = col
                    currentTracedCells.clear()
                    currentTracedCells.add(Pair(row, col))
                    notifyTracing()
                    invalidate()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && row in 0 until b.size && col in 0 until b.size) {
                    val dRow = row - startRow
                    val dCol = col - startCol

                    // Check if straight line: horizontal, vertical, or diagonal
                    val isHorizontal = (dRow == 0 && dCol != 0)
                    val isVertical = (dCol == 0 && dRow != 0)
                    val isDiagonal = (abs(dRow) == abs(dCol) && dRow != 0)

                    if (isHorizontal || isVertical || isDiagonal) {
                        val steps = max(abs(dRow), abs(dCol))
                        val stepR = sign(dRow.toFloat()).toInt()
                        val stepC = sign(dCol.toFloat()).toInt()

                        currentTracedCells.clear()
                        for (i in 0..steps) {
                            currentTracedCells.add(Pair(startRow + i * stepR, startCol + i * stepC))
                        }
                        notifyTracing()
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    checkTracedWordMatch()
                    currentTracedCells.clear()
                    invalidate()
                }
            }
        }
        return true
    }

    private fun notifyTracing() {
        val b = board ?: return
        val sb = StringBuilder()
        for ((r, c) in currentTracedCells) {
            sb.append(b.grid[r][c])
        }
        listener?.onWordTracing(sb.toString())
    }

    private fun checkTracedWordMatch() {
        val b = board ?: return
        if (currentTracedCells.isEmpty()) {
            listener?.onTracingCancelled()
            return
        }

        val forwardWord = currentTracedCells.map { (r, c) -> b.grid[r][c] }.joinToString("")
        val reverseWord = forwardWord.reversed()

        // Match against unsolved target words
        val matchedTarget = b.targetWords.find {
            !it.isFound && (it.targetWord == forwardWord || it.targetWord == reverseWord)
        }

        if (matchedTarget != null) {
            matchedTarget.isFound = true
            solvedPaths.add(Pair(currentTracedCells.first(), currentTracedCells.last()))
            if (hintCell == Pair(matchedTarget.startRow, matchedTarget.startCol)) {
                hintCell = null
            }
            listener?.onWordFound(matchedTarget)
        } else {
            listener?.onTracingCancelled()
        }
    }
}
