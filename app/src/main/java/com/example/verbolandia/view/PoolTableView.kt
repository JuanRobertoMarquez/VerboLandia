package com.example.verbolandia.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PoolTableView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnPocketSunkListener {
        fun onPocketSunk(pocketIndex: Int)
    }

    var onPocketSunkListener: OnPocketSunkListener? = null

    // Number of active pockets: 4 (Rounds 1-5) or 6 (Rounds 6+)
    var activePocketCount = 4
        set(value) {
            field = value.coerceIn(4, 6)
            invalidate()
        }

    // Physics constants
    private val friction = 0.985f
    private val bounceDamping = 0.85f
    private val maxLaunchSpeed = 55f
    private val minSpeedThreshold = 0.25f

    // Dimensions
    private val density = resources.displayMetrics.density
    private val ballRadius = 20f * density
    private val pocketRadius = 38f * density
    private val cushionPadding = 18f * density
    private val diamondRadius = 3.5f * density

    // State
    var ballX = 0f
    var ballY = 0f
    var vx = 0f
    var vy = 0f

    private var isDragging = false
    private var dragTouchX = 0f
    private var dragTouchY = 0f
    private var isSunk = false
    private var isMotionActive = false
    private var sunkScale = 1.0f

    // 6 Pocket Positions: 0:TopLeft, 1:TopRight, 2:BottomLeft, 3:BottomRight, 4:MidLeft, 5:MidRight
    private val pockets = Array(6) { PointF(0f, 0f) }

    // Paints
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val woodFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2011")
        style = Paint.Style.FILL
    }
    private val innerCushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B0E07")
        style = Paint.Style.STROKE
        strokeWidth = cushionPadding
    }

    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }

    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#090D16")
        style = Paint.Style.FILL
    }

    private val pocketRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val aimLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        pathEffect = DashPathEffect(floatArrayOf(12f * density, 8f * density), 0f)
    }

    private val aimArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f * density
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setupPocketsAndBall(w, h)
    }

    fun resetCueBall() {
        isSunk = false
        isMotionActive = false
        sunkScale = 1.0f
        vx = 0f
        vy = 0f
        setupPocketsAndBall(width, height)
        invalidate()
    }

    private fun setupPocketsAndBall(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        val offset = pocketRadius * 0.75f
        pockets[0].set(offset, offset)                 // Top-Left
        pockets[1].set(w - offset, offset)             // Top-Right
        pockets[2].set(offset, h - offset)             // Bottom-Left
        pockets[3].set(w - offset, h - offset)         // Bottom-Right
        pockets[4].set(offset, h / 2f)                 // Middle-Left
        pockets[5].set(w - offset, h / 2f)             // Middle-Right

        if (!isMotionActive && !isSunk) {
            ballX = w / 2f
            ballY = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw Wood Outer Frame
        canvas.drawRect(0f, 0f, w, h, woodFramePaint)

        // 2. Draw Felt Surface with Radial Lighting Vignette
        val feltGradient = RadialGradient(
            w / 2f, h / 2f, hypot(w.toDouble(), h.toDouble()).toFloat() * 0.6f,
            Color.parseColor("#0E5E35"), Color.parseColor("#063D1F"),
            Shader.TileMode.CLAMP
        )
        feltPaint.shader = feltGradient
        canvas.drawRect(cushionPadding, cushionPadding, w - cushionPadding, h - cushionPadding, feltPaint)
        canvas.drawRect(cushionPadding / 2f, cushionPadding / 2f, w - cushionPadding / 2f, h - cushionPadding / 2f, innerCushionPaint)

        // 3. Draw Sight Dots / Diamonds on Cushions
        drawSightDiamonds(canvas, w, h)

        // 4. Draw Active Pockets
        val activeCount = activePocketCount.coerceIn(4, 6)
        for (i in 0 until activeCount) {
            val pocket = pockets[i]
            canvas.drawCircle(pocket.x, pocket.y, pocketRadius, pocketPaint)
            canvas.drawCircle(pocket.x, pocket.y, pocketRadius, pocketRimPaint)
        }

        if (isSunk) return

        // 5. Draw Aiming Cue Line when Dragging
        if (isDragging) {
            val dx = ballX - dragTouchX
            val dy = ballY - dragTouchY
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist > 10f) {
                val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                val lineLen = dist.coerceAtMost(320f * density)
                val targetX = ballX + cos(angle.toDouble()).toFloat() * lineLen
                val targetY = ballY + sin(angle.toDouble()).toFloat() * lineLen

                canvas.drawLine(ballX, ballY, targetX, targetY, aimLinePaint)
                canvas.drawCircle(targetX, targetY, 6f * density, aimArrowPaint)
            }
        }

        // 6. Draw Ball Shadow
        val currentRadius = ballRadius * sunkScale
        canvas.drawCircle(ballX + 4f * density, ballY + 4f * density, currentRadius, ballShadowPaint)

        // 7. Draw 3D Cue Ball
        val shader = RadialGradient(
            ballX - currentRadius * 0.3f,
            ballY - currentRadius * 0.3f,
            currentRadius * 1.2f,
            Color.WHITE,
            Color.parseColor("#CBD5E1"),
            Shader.TileMode.CLAMP
        )
        ballPaint.shader = shader
        canvas.drawCircle(ballX, ballY, currentRadius, ballPaint)
    }

    private fun drawSightDiamonds(canvas: Canvas, w: Float, h: Float) {
        val pad = cushionPadding / 2f

        // Top & Bottom Diamonds
        val stepX = w / 4f
        for (i in 1..3) {
            canvas.drawCircle(i * stepX, pad, diamondRadius, diamondPaint)
            canvas.drawCircle(i * stepX, h - pad, diamondRadius, diamondPaint)
        }

        // Left & Right Diamonds
        val stepY = h / 4f
        for (i in 1..3) {
            canvas.drawCircle(pad, i * stepY, diamondRadius, diamondPaint)
            canvas.drawCircle(w - pad, i * stepY, diamondRadius, diamondPaint)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isSunk || isMotionActive) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - ballX
                val dy = event.y - ballY
                if (hypot(dx.toDouble(), dy.toDouble()) <= ballRadius * 2.8f) {
                    isDragging = true
                    dragTouchX = event.x
                    dragTouchY = event.y
                    performClick()
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    dragTouchX = event.x
                    dragTouchY = event.y
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val dx = ballX - event.x
                    val dy = ballY - event.y
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                    if (dist > 15f) {
                        val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                        val speed = (dist * 0.18f).coerceAtMost(maxLaunchSpeed)
                        vx = cos(angle.toDouble()).toFloat() * speed
                        vy = sin(angle.toDouble()).toFloat() * speed
                        startPhysicsLoop()
                    } else {
                        invalidate()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startPhysicsLoop() {
        isMotionActive = true
        post(object : Runnable {
            override fun run() {
                if (!isMotionActive || isSunk) return

                updatePhysics()
                invalidate()

                if (isMotionActive && !isSunk) {
                    postOnAnimation(this)
                }
            }
        })
    }

    private fun updatePhysics() {
        ballX += vx
        ballY += vy

        vx *= friction
        vy *= friction

        if (hypot(vx.toDouble(), vy.toDouble()) < minSpeedThreshold) {
            vx = 0f
            vy = 0f
            isMotionActive = false
            return
        }

        val leftBound = cushionPadding + ballRadius
        val rightBound = width - cushionPadding - ballRadius
        val topBound = cushionPadding + ballRadius
        val bottomBound = height - cushionPadding - ballRadius

        // Wall Cushion Bounces
        if (ballX < leftBound) {
            ballX = leftBound
            vx = -vx * bounceDamping
        } else if (ballX > rightBound) {
            ballX = rightBound
            vx = -vx * bounceDamping
        }

        if (ballY < topBound) {
            ballY = topBound
            vy = -vy * bounceDamping
        } else if (ballY > bottomBound) {
            ballY = bottomBound
            vy = -vy * bounceDamping
        }

        // Pocket Sinking Collision Check for active pockets
        val activeCount = activePocketCount.coerceIn(4, 6)
        for (i in 0 until activeCount) {
            val pocket = pockets[i]
            val dx = ballX - pocket.x
            val dy = ballY - pocket.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist < pocketRadius * 0.88f) {
                isSunk = true
                isMotionActive = false
                vx = 0f
                vy = 0f
                onPocketSunkListener?.onPocketSunk(i)
                break
            }
        }
    }
}
