package com.victor.mascota

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class Anim(val bmp: Bitmap, val w: Int, val h: Int, val dur: IntArray)

@SuppressLint("ViewConstructor")
class PetView(ctx: Context, private val wm: WindowManager) : View(ctx) {

    private enum class St { IDLE, WALK, SIT, SLEEP, HOP, FALL, DRAG }

    private val dens = ctx.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * dens

    private val k = max(4, (dens * 2f).roundToInt())
    private val winW = 36 * k
    private val spriteH = 64 * k
    private val bubbleH = dp(40f).toInt()
    private val winH = spriteH + bubbleH

    val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
        winW,
        winH,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun load(name: String, w: Int, h: Int, dur: IntArray): Anim {
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        val bmp = context.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it, null, opts)!! }
        return Anim(bmp, w, h, dur)
    }

    private val idle = load("Idle", 32, 40, intArrayOf(40, 4, 2))
    private val walkR = load("WalkR", 32, 32, intArrayOf(6, 10, 6, 10))
    private val walkL = load("WalkL", 32, 32, intArrayOf(6, 10, 6, 10))
    private val hop = load("Hop", 32, 80, intArrayOf(2, 1, 2, 3, 4, 4, 3, 2, 1, 2))
    private val sit = load("Sit", 24, 32, intArrayOf(8, 8, 8))
    private val sleep = load("Sleep", 32, 32, intArrayOf(30, 35))

    private val paint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3ECD8.toInt() }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1C1424.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF2A2230.toInt()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val src = Rect()
    private val dst = Rect()
    private val rect = RectF()

    private var st = St.IDLE
    private var anim = idle
    private var fr = 0
    private var ft = 0L
    private var until = 0L
    private var xf = 0f
    private var yf = 0f
    private var dir = 1
    private var tx = 0f
    private var lastT = 0L
    private var bubble: String? = null
    private var bubbleUntil = 0L
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private val phrases = arrayOf("¡Hola!", "¿Jugamos?", "¡Cosquillas!", "Tengo hambre", "¡Yuju!", "Estoy acá")

    private val mainHandler = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            tick()
            mainHandler.postDelayed(this, 33)
        }
    }

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            react()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            openGame()
            return true
        }
    })

    init {
        val size = screen()
        xf = (size.first - winW) / 2f
        yf = groundY(size.second)
        params.x = xf.toInt()
        params.y = yf.toInt()
        val now = SystemClock.uptimeMillis()
        lastT = now
        ft = now
        until = now + 2000
    }

    fun start() {
        mainHandler.postDelayed(loop, 33)
    }

    fun shutdown() {
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun screen(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels, m.heightPixels)
        }
    }

    private fun groundY(sh: Int): Float = max(0f, sh - winH - dp(70f))

    private fun setAnim(a: Anim) {
        if (a !== anim) {
            anim = a
            fr = 0
            ft = SystemClock.uptimeMillis()
        }
    }

    private fun goIdle(now: Long, ms: Long) {
        st = St.IDLE
        setAnim(idle)
        until = now + ms
    }

    private fun decide(now: Long, maxX: Float) {
        val r = Random.nextFloat()
        when {
            r < 0.45f -> {
                var t = Random.nextFloat() * maxX
                if (abs(t - xf) < dp(80f)) {
                    t = if (xf > maxX / 2f) 0f else maxX
                }
                tx = t
                dir = if (tx > xf) 1 else -1
                st = St.WALK
                setAnim(if (dir > 0) walkR else walkL)
            }
            r < 0.65f -> {
                st = St.SIT
                setAnim(sit)
                until = now + Random.nextLong(4000, 8000)
            }
            r < 0.80f -> {
                st = St.SLEEP
                setAnim(sleep)
                until = now + Random.nextLong(8000, 16000)
            }
            else -> goIdle(now, Random.nextLong(3000, 6000))
        }
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastT) / 1000f).coerceAtMost(0.1f)
        lastT = now
        val size = screen()
        val maxX = max(0f, (size.first - winW).toFloat())
        val gy = groundY(size.second)
        if (bubble != null && now > bubbleUntil) bubble = null

        when (st) {
            St.WALK -> {
                xf += dir * dp(70f) * dt
                if ((dir > 0 && xf >= tx) || (dir < 0 && xf <= tx)) {
                    xf = tx
                    goIdle(now, Random.nextLong(2000, 5000))
                }
                yf = gy
            }
            St.FALL -> {
                yf += dp(900f) * dt
                if (yf >= gy) {
                    yf = gy
                    goIdle(now, 2500)
                }
            }
            St.DRAG -> {
            }
            else -> {
                yf = gy
                if (now >= until) decide(now, maxX)
            }
        }
        xf = xf.coerceIn(0f, maxX)

        val d = anim.dur[fr] * (1000f / 60f) * 1.6f
        if (now - ft > d) {
            fr = (fr + 1) % anim.dur.size
            ft = now
        }

        val nx = xf.toInt()
        val ny = yf.toInt()
        if (nx != lastX || ny != lastY) {
            params.x = nx
            params.y = ny
            lastX = nx
            lastY = ny
            try {
                wm.updateViewLayout(this, params)
            } catch (e: Exception) {
            }
        }
        invalidate()
    }

    private fun react() {
        val now = SystemClock.uptimeMillis()
        val wasSleeping = st == St.SLEEP
        st = St.HOP
        setAnim(hop)
        until = now + 1400
        bubble = if (wasSleeping) "¡Ay! Me desperté" else phrases[Random.nextInt(phrases.size)]
        bubbleUntil = now + 2200
    }

    private fun openGame() {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(PetService.GAME_URL))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(i)
        } catch (e: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        val size = screen()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startX = xf
                startY = yf
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!dragging && hypot(dx, dy) > slop) {
                    dragging = true
                    st = St.DRAG
                    setAnim(idle)
                }
                if (dragging) {
                    xf = (startX + dx).coerceIn(0f, max(0f, (size.first - winW).toFloat()))
                    yf = (startY + dy).coerceIn(0f, max(0f, (size.second - winH).toFloat()))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    st = St.FALL
                }
            }
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val a = anim
        val left = fr * a.w
        src.set(left, 0, left + a.w, a.h)
        val dw = a.w * k
        val dh = a.h * k
        val cx = width / 2f
        val cy = bubbleH + spriteH / 2f
        dst.set(
            (cx - dw / 2f).toInt(),
            (cy - dh / 2f).toInt(),
            (cx + dw / 2f).toInt(),
            (cy + dh / 2f).toInt()
        )
        c.drawBitmap(a.bmp, src, dst, paint)
        val t = bubble
        if (t != null) drawBubble(c, t)
    }

    private fun drawBubble(c: Canvas, t: String) {
        var size = dp(13f)
        textP.textSize = size
        val maxW = width - dp(16f)
        while (textP.measureText(t) > maxW && size > dp(8f)) {
            size -= 1f
            textP.textSize = size
        }
        val tw = textP.measureText(t)
        val padx = dp(8f)
        val h = dp(26f)
        val top = dp(4f)
        rect.set((width - tw) / 2f - padx, top, (width + tw) / 2f + padx, top + h)
        c.drawRoundRect(rect, dp(6f), dp(6f), bubbleFill)
        c.drawRoundRect(rect, dp(6f), dp(6f), bubbleStroke)
        c.drawText(t, width / 2f, top + h / 2f - (textP.ascent() + textP.descent()) / 2f, textP)
    }
}
