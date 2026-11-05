package com.lun.pet

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*
import kotlin.math.abs

class PetService : Service() {
 private lateinit var wm: WindowManager
 private lateinit var root: LinearLayout
 private lateinit var params: WindowManager.LayoutParams
 private val handler = Handler(Looper.getMainLooper())
 private var bubble: TextView? = null
 private var hideBubble: Runnable? = null
 private var animator: ObjectAnimator? = null
 private var started = false
 private var touching = false
 private var nextMoveAt = 0L
 private var targetX = 0
 private var targetY = 0
 private val wander = object : Runnable {
  override fun run() {
   if (started && !touching) {
    val now = android.os.SystemClock.uptimeMillis()
    val bounds = wm.currentWindowMetrics.bounds
    val maxX = (bounds.width() - dp(210)).coerceAtLeast(0)
    val maxY = (bounds.height() - dp(235) - dp(35)).coerceAtLeast(0)
    if (now >= nextMoveAt) {
     targetX = (0..maxX).random(); targetY = (dp(60).coerceAtMost(maxY)..maxY).random()
     nextMoveAt = now + (4500L..9500L).random()
    }
    val dx = targetX - params.x; val dy = targetY - params.y
    if (kotlin.math.abs(dx) > 2 || kotlin.math.abs(dy) > 2) {
     params.x = (params.x + dx.coerceIn(-2, 2)).coerceIn(0,maxX)
     params.y = (params.y + dy.coerceIn(-2, 2)).coerceIn(0,maxY)
     wm.updateViewLayout(root,params)
    }
   }
   handler.postDelayed(this, 40)
  }
 }
 private val lines = arrayOf("老板，今天也要值班？", "别碰帽子。……算了。", "手机安全检查，例行公事。", "我只是休息五分钟。", "老板，你这门锁该换了。", "墨镜？职业习惯。")
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager; showNotification(); createPet() }
 private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
 private fun showNotification() {
  val channel = "lun_pet"; val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  nm.createNotificationChannel(NotificationChannel(channel, "伦警官桌宠运行中", NotificationManager.IMPORTANCE_LOW))
  val stop = Intent(this, PetService::class.java).apply { action = "STOP" }
  val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
  val pi = PendingIntent.getService(this, 0, stop, flags)
  val notification = Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_menu_myplaces).setContentTitle("伦警官正在值班").setContentText("点击桌宠互动，长按隐藏").setOngoing(true).addAction(Notification.Action.Builder(null, "下班", pi).build()).build()
  startForeground(101, notification)
 }
 private fun createPet() {
  root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
  val label = TextView(this).apply { textSize = 12f; setTextColor(Color.WHITE); setPadding(dp(10), dp(6), dp(10), dp(6)); gravity = Gravity.CENTER; visibility = View.GONE
   background = GradientDrawable().apply { setColor(Color.rgb(65,52,73)); cornerRadius = dp(12).toFloat() } }
  bubble = label
  val pet = ImageView(this).apply { setImageResource(R.drawable.lun); scaleType = ImageView.ScaleType.FIT_CENTER }
  root.addView(label, LinearLayout.LayoutParams(dp(200), dp(55)))
  root.addView(pet, LinearLayout.LayoutParams(dp(170), dp(170)))
  params = WindowManager.LayoutParams(dp(210), dp(235), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(80); y = dp(270) }
  wm.addView(root, params)
  animator = ObjectAnimator.ofFloat(pet, "translationY", 0f, -dp(5).toFloat(), 0f).apply { duration = 2300; repeatCount = ValueAnimator.INFINITE; start() }
  var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var downAt = 0L; var dragged = false
  pet.setOnTouchListener { _, event ->
   when (event.actionMasked) {
    MotionEvent.ACTION_DOWN -> { touching = true; downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y; downAt = System.currentTimeMillis(); dragged = false; true }
    MotionEvent.ACTION_MOVE -> { val dx = event.rawX - downX; val dy = event.rawY - downY; if (abs(dx) > dp(7) || abs(dy) > dp(7)) dragged = true
     if (dragged) { params.x = originX + dx.toInt(); params.y = originY + dy.toInt(); wm.updateViewLayout(root, params) }; true }
    MotionEvent.ACTION_UP -> { touching = false; nextMoveAt = android.os.SystemClock.uptimeMillis() + 3500L; if (!dragged && System.currentTimeMillis() - downAt > 650) stopSelf() else if (!dragged) { pet.animate().rotation(-7f).setDuration(100).withEndAction { pet.animate().rotation(0f).setDuration(160).start() }.start(); say(lines.random()) }; true }
    else -> true
   }
  }
  started = true
  nextMoveAt = android.os.SystemClock.uptimeMillis() + 2500L
  targetX = params.x; targetY = params.y
  handler.postDelayed(wander, 1000L)
 }
 private fun say(line: String) {
  val b = bubble ?: return; hideBubble?.let(handler::removeCallbacks)
  b.text = line; b.visibility = View.VISIBLE
  hideBubble = Runnable { b.visibility = View.GONE }.also { handler.postDelayed(it, 2700) }
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
  return START_STICKY
 }
 override fun onDestroy() { handler.removeCallbacks(wander); hideBubble?.let(handler::removeCallbacks); animator?.cancel(); if (started) { wm.removeView(root); started = false }; super.onDestroy() }
}
