package com.lun.pet

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import android.graphics.Color
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

class ApiSettingsActivity : Activity() {
 private val prefs by lazy { getSharedPreferences("lun_api", MODE_PRIVATE) }
 private lateinit var url: EditText
 private lateinit var key: EditText
 private lateinit var model: EditText
 private lateinit var status: TextView
 private lateinit var list: Spinner
 private val executor = Executors.newSingleThreadExecutor()
 private var models = listOf<String>()
 private val alias = "lun_api_key_v1"

 private fun encrypt(raw: String): String {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  if (!store.containsAlias(alias)) {
   val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
   generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
   generator.generateKey()
  }
  val secret = store.getKey(alias, null)
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE, secret)
  return Base64.encodeToString(cipher.iv + cipher.doFinal(raw.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
 }
 private fun decrypt(encoded: String): String = try {
  val bytes = Base64.decode(encoded, Base64.DEFAULT)
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE, store.getKey(alias, null), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
  String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
 } catch (_: Exception) { "" }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val scroll = ScrollView(this)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,36,28,36); setBackgroundColor(Color.rgb(35,31,39)) }
  fun heading(text: String) = TextView(this).apply { this.text = text; textSize = 17f; setTextColor(Color.WHITE); setPadding(0,20,0,6) }
  fun field(hint: String, value: String): EditText = EditText(this).apply {
   this.hint = hint; setText(value); setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
  }
  layout.addView(heading("伦警官 · AI API 设置"))
  layout.addView(heading("API 地址（Base URL）"))
  url = field("https://api.example.com/v1", prefs.getString("url","") ?: "")
  layout.addView(url)
  layout.addView(heading("API Key（仅保存在本机）"))
  key = field("输入你的 API Key", decrypt(prefs.getString("key","") ?: ""))
  key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
  layout.addView(key)
  val fetch = Button(this).apply { text = "获取模型列表" }
  layout.addView(fetch)
  status = heading("支持 OpenAI 兼容的 /models 接口；也可以手动填写模型 ID。")
  layout.addView(status)
  list = Spinner(this)
  layout.addView(list)
  layout.addView(heading("模型 ID"))
  model = field("手动填写，或从列表选择",prefs.getString("model","") ?: "")
  layout.addView(model)
  list.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
   override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
    if (models.isNotEmpty()) model.setText(models[position])
   }
  }
  fetch.setOnClickListener {
   val base = url.text.toString().trim().trimEnd('/')
   val token = key.text.toString().trim()
   if (!base.startsWith("https://")) { status.text = "请使用 HTTPS API 地址"; return@setOnClickListener }
   status.text = "正在获取模型列表…"
   executor.execute {
    try {
     val connection = URL(base + "/models").openConnection() as HttpURLConnection
     connection.requestMethod = "GET"; connection.connectTimeout = 12000; connection.readTimeout = 12000
     connection.setRequestProperty("Authorization", "Bearer $token")
     try {
      val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
       ?.bufferedReader()?.use { it.readText() } ?: ""
      if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}: " + response.take(120))
      val data = JSONObject(response).getJSONArray("data")
      val found = (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }.sorted()
      runOnUiThread {
       models = found
       list.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, found)
       status.text = if (found.isEmpty()) "没有返回模型，可手动填写" else "已获取 ${found.size} 个模型"
      }
     } finally { connection.disconnect() }
    } catch (e: Exception) { runOnUiThread { status.text = "获取失败：${e.message?.take(120)}；可手动填写模型" } }
   }
  }
  layout.addView(Button(this).apply { text = "保存配置"; setOnClickListener {
   val base = url.text.toString().trim().trimEnd('/')
   val id = model.text.toString().trim()
   if (!base.startsWith("https://") || id.isBlank()) { status.text = "请填写 HTTPS 地址和模型 ID"; return@setOnClickListener }
   try {
    prefs.edit().putString("url",base).putString("key",encrypt(key.text.toString().trim())).putString("model",id).apply()
    status.text = "配置已保存在本机"
    Toast.makeText(this@ApiSettingsActivity,"保存成功",Toast.LENGTH_SHORT).show()
   } catch (e: Exception) { status.text = "保存失败：${e.message}" }
  } })
  scroll.addView(layout); setContentView(scroll)
 }
 override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
}
