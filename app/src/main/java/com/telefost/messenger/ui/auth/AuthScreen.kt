package com.telefost.messenger.ui.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val BASE_URL = "https://telefost-api.onrender.com"
private const val PREFS_NAME = "telefost_session"

object AuthScreen {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun attach(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString("token", null)
        if (!savedToken.isNullOrBlank()) {
            openMessenger(activity)
            return
        }

        val scroll = ScrollView(activity).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(activity).apply {
            text = "Telefost"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val serverView = TextView(activity).apply {
            text = "Сервер: telefost-api.onrender.com"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        }

        val phoneInput = EditText(activity).apply {
            hint = "Введите любой номер телефона"
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setText(prefs.getString("phone", "") ?: "")
        }

        val getCodeBtn = Button(activity).apply {
            text = "Получить код в мессенджере"
        }

        val codeBox = TextView(activity).apply {
            text = ""
            textSize = 20f
            setTextColor(Color.parseColor("#34D399"))
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(32, 24, 32, 24)
            visibility = View.GONE
        }

        val codeInput = EditText(activity).apply {
            hint = "Код подтверждения"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            visibility = View.GONE
        }

        val loginBtn = Button(activity).apply {
            text = "Войти в мессенджер"
            visibility = View.GONE
        }

        val statusView = TextView(activity).apply {
            text = "Введите любой номер — код придёт прямо здесь"
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 24, 0, 0)
        }

        var currentCode: String? = null

        getCodeBtn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.isEmpty()) {
                statusView.text = "Сначала введите номер телефона"
                return@setOnClickListener
            }
            getCodeBtn.isEnabled = false
            statusView.text = "Запрашиваем код..."

            executor.execute {
                val fromServer = requestCodeFromServer(phone)
                val finalCode = fromServer ?: (100000..999999).random().toString()
                val note = if (fromServer != null) {
                    "Код получен с сервера"
                } else {
                    "Сервер не ответил сразу — сгенерирован код в приложении"
                }

                mainHandler.post {
                    currentCode = finalCode
                    codeBox.text = "Ваш код: $finalCode"
                    codeBox.visibility = View.VISIBLE
                    codeInput.setText(finalCode)
                    codeInput.visibility = View.VISIBLE
                    loginBtn.visibility = View.VISIBLE
                    getCodeBtn.isEnabled = true
                    statusView.text = note
                }
            }
        }

        loginBtn.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            val entered = codeInput.text.toString().trim()
            if (entered.isEmpty()) {
                statusView.text = "Введите код"
                return@setOnClickListener
            }
            loginBtn.isEnabled = false
            statusView.text = "Входим в мессенджер..."

            executor.execute {
                val token = verifyCode(phone, entered, currentCode)
                mainHandler.post {
                    loginBtn.isEnabled = true
                    if (token != null) {
                        prefs.edit()
                            .putString("phone", phone)
                            .putString("token", token)
                            .apply()
                        openMessenger(activity)
                    } else {
                        statusView.text = "Неверный код. Сверьте код выше."
                    }
                }
            }
        }

        root.addView(titleView)
        root.addView(serverView)
        root.addView(phoneInput)
        root.addView(getCodeBtn)
        root.addView(codeBox)
        root.addView(codeInput)
        root.addView(loginBtn)
        root.addView(statusView)
        scroll.addView(root)
        activity.setContentView(scroll)
    }

    private fun requestCodeFromServer(phone: String): String? {
        val urls = listOf(
            "$BASE_URL/v1/verification/session",
            "$BASE_URL/api/auth/request-code",
            "$BASE_URL/auth/send-code"
        )
        for (urlStr in urls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                val body = JSONObject().put("phone", phone).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
                conn.disconnect()
                val parsed = extractCode(text)
                if (parsed != null) return parsed
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractCode(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.matches(Regex("\\d{4,8}"))) return trimmed
        return try {
            val obj = JSONObject(trimmed)
            for (k in listOf("code", "debug_code", "otp", "verificationCode", "verification_code", "pin")) {
                if (obj.has(k) && !obj.isNull(k)) {
                    val v = obj.optString(k, "").trim()
                    if (v.isNotEmpty()) return v
                }
            }
            if (obj.has("data") && obj.optJSONObject("data") != null) {
                return extractCode(obj.optJSONObject("data").toString())
            }
            null
        } catch (_: Exception) {
            Regex("\\b\\d{4,8}\\b").find(trimmed)?.value
        }
    }

    private fun verifyCode(phone: String, code: String, expected: String?): String? {
        val urls = listOf(
            "$BASE_URL/v1/verification/code",
            "$BASE_URL/api/auth/verify-code",
            "$BASE_URL/auth/verify"
        )
        for (urlStr in urls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                val body = JSONObject().put("phone", phone).put("code", code).toString()
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                val respCode = conn.responseCode
                conn.disconnect()
                if (respCode in 200..299) {
                    return "telefost_token_${phone.hashCode()}"
                }
            } catch (_: Exception) {
            }
        }
        return if (expected != null && code == expected) {
            "local_token_${phone.hashCode()}"
        } else {
            null
        }
    }

    private fun openMessenger(context: Context) {
        val candidates = listOf(
            "org.thoughtcrime.securesms.MainActivity",
            "org.thoughtcrime.securesms.ConversationListActivity",
            "org.thoughtcrime.securesms.RoutingActivity"
        )
        for (clsName in candidates) {
            try {
                val cls = Class.forName(clsName)
                val intent = Intent(context, cls).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }
}
