package com.lun.pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 70, 40, 40) }
  layout.addView(TextView(this).apply { text = "伦警官桌宠 · v0.1\n\n点击伦：随机对话\n拖动伦：移动位置\n长按伦：隐藏桌宠\n\n首次使用请授权‘显示在其他应用上层’。"; textSize = 18f })
  val start = Button(this).apply { text = "召唤伦警官"; setOnClickListener { startPet() } }
  val stop = Button(this).apply { text = "让伦下班"; setOnClickListener { stopService(Intent(this@MainActivity, PetService::class.java)) } }
  layout.addView(start); layout.addView(stop); setContentView(layout)
  if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
 }
 private fun startPet() {
  if (!Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
  startForegroundService(Intent(this, PetService::class.java))
 }
}
