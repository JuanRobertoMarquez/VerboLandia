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
        fun onPocketSunk(pocketIndex: Int) // 0: TopLeft, 1: TopRight, 2: BottomLeft, 3: BottomRight
    }

    var onPocketSunkListener: OnPocketSunkListener? = null

    // Physics constants
    private val friction = 0.985f
    private val bounceDamping = 0.85f
    private val maxLaunchSpeed = 55f
    private val minSpeedThreshold = 0.25f

    // Dimensions
    private val density = resources.displayMetrics.density
    private val ballRadius = 20f * density
    private val pocketRadius = 40f * density
    private val cushionPadding = 14f * density

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

    // Pocket positions
    private val pockets = arrayOf(
        PointF(0f, 0f), // Top-Left (0)
        PointF(0f, 0f), // Top-Right (1)
        PointF(0f, 0f), // Bottom-Left (2)
        PointF(0f, 0f)  // Bottom-Right (3)
    )

    // Paints
    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0C542D")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A2E19")
        style = Paint.Style.STROKE
        strokeWidth = cushionPadding
    }

    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        style = Paint.Style.FILL
    }

    private val pocketRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#374151")
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
        strokeWidth = 3f * density
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
        vx = 0f
        vy = 0f
        setupPocketsAndBall(width, height)
        invalidate()
    }

    private fun setupPocketsAndBall(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val offset = pocketRadius * 0.7f
        pockets[0].set(offset, offset)
        pockets[1].set(w - offset, offset)
        pockets[2].set(offset, h - offset)
        pockets[3].set(w - offset, h - offset)

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

        // 1. Felt Background & Cushion Border
        canvas.drawRect(0f, 0f, w, h, feltPaint)
        canvas.drawRect(cushionPadding / 2f, cushionPadding / 2f, w - cushionPadding / 2f, h - cushionPadding / 2f, borderPaint)

        // 2. Draw 4 Corner Pockets
        for (pocket in pockets) {
            canvas.drawCircle(pocket.x, pocket.y, pocketRadius, pocketPaint)
            canvas.drawCircle(pocket.x, pocket.y, pocketRadius, pocketRimPaint)
        }

        if (isSunk) return

        // 3. Draw Aiming Cue Line when Dragging
        if (isDragging) {
            val dx = ballX - dragTouchX
            val dy = ballY - dragTouchY
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist > 10f) {
                val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                val lineLen = dist.coerceAtMost(300f * density)
                val targetX = ballX + cos(angle.toDouble()).toFloat() * lineLen
                val targetY = ballY + sin(angle.toDouble()).toFloat() * lineLen

                canvas.drawLine(ballX, ballY, targetX, targetY, aimLinePaint)
                canvas.drawCircle(targetX, targetY, 6f * density, aimArrowPaint)
            }
        }

        // 4. Draw Ball Shadow
        canvas.drawCircle(ballX + 4f * density, ballY + 4f * density, ballRadius, ballShadowPaint)

        // 5. Draw 3D Cue Ball (White Sphere with Shading)
        val shader = RadialGradient(
            ballX - ballRadius * 0.3f,
            ballY - ballRadius * 0.3f,
            ballRadius * 1.2f,
            Color.WHITE,
            Color.parseColor("#CBD5E1"),
            Shader.TileMode.CLAMP
        )
        ballPaint.shader = shader
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isSunk || isMotionActive) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val dx = event.x - ballX
                val dy = event.y - ballY
                if (hypot(dx.toDouble(), dy.toDouble()) <= ballRadius * 2.5f) {
                    isDragging = true
                    dragTouchX = event.x
                    dragTouchY = event.y
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

        // Pocket Sinking Collision Check
        for (i in pockets.indices) {
            val pocket = pockets[i]
            val dx = ballX - pocket.x
            val dy = ballY - pocket.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            if (dist < pocketRadius * 0.85f) {
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
