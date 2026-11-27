package com.telefost.messenger.ui.auth

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val BASE_URL = "https://telefost-api.onrender.com"
private const val PREFS_NAME = "telefost_session"

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var phone by remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
    var codeInput by remember { mutableStateOf("") }
    var issuedCode by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Введите любой номер — код придёт прямо здесь") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val savedToken = prefs.getString("token", null)
        if (!savedToken.isNullOrBlank()) {
            openMessenger(context)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Telefost", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Сервер: telefost-api.onrender.com",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Номер телефона (любой)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                enabled = !isLoading && phone.isNotBlank(),
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusText = "Запрашиваем код у сервера..."
                        val result = requestOtpCode(phone.trim())
                        isLoading = false
                        if (result.code != null) {
                            issuedCode = result.code
                            codeInput = result.code
                            statusText = result.message
                        } else {
                            val fallback = (100000..999999).random().toString()
                            issuedCode = fallback
                            codeInput = fallback
                            statusText = "${result.message} • Код в приложении: $fallback"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Загрузка..." else "Получить код в мессенджере")
            }

            if (issuedCode != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ваш код подтверждения:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = issuedCode ?: "",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it },
                    label = { Text("Код из сообщения") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    enabled = !isLoading && codeInput.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            statusText = "Входим в мессенджер..."
                            val token = verifyOtpCode(
                                phone = phone.trim(),
                                code = codeInput.trim(),
                                expectedCode = issuedCode
                            )
                            isLoading = false
                            if (token != null) {
                                prefs.edit()
                                    .putString("phone", phone.trim())
                                    .putString("token", token)
                                    .apply()
                                openMessenger(context)
                            } else {
                                statusText = "Неверный код. Проверьте код выше и попробуйте снова."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Войти в мессенджер")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private data class OtpResult(val code: String?, val message: String)

private suspend fun requestOtpCode(phone: String): OtpResult = withContext(Dispatchers.IO) {
    val endpoints = listOf(
        "$BASE_URL/v1/verification/session",
        "$BASE_URL/api/auth/request-code",
        "$BASE_URL/auth/send-code"
    )
    for (urlStr in endpoints) {
        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject().put("phone", phone).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            val codeHttp = conn.responseCode
            val stream = if (codeHttp in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
            conn.disconnect()

            if (body.isNotBlank()) {
                val otp = extractCodeFromJson(body)
                if (otp != null) {
                    return@withContext OtpResult(otp, "Код получен с сервера")
                }
            }
        } catch (_: Exception) {
        }
    }
    OtpResult(null, "Сервер не вернул код напрямую")
}

private fun extractCodeFromJson(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.matches(Regex("\\d{4,8}"))) return trimmed
    return try {
        val json = JSONObject(trimmed)
        val keys = listOf("code", "debug_code", "otp", "verificationCode", "verification_code", "pin")
        for (k in keys) {
            if (json.has(k) && !json.isNull(k)) {
                val v = json.optString(k, "").trim()
                if (v.isNotEmpty()) return v
            }
        }
        if (json.has("data") && json.optJSONObject("data") != null) {
            return extractCodeFromJson(json.optJSONObject("data").toString())
        }
        null
    } catch (_: Exception) {
        Regex("\\b\\d{4,8}\\b").find(trimmed)?.value
    }
}

private suspend fun verifyOtpCode(
    phone: String,
    code: String,
    expectedCode: String?
): String? = withContext(Dispatchers.IO) {
    val endpoints = listOf(
        "$BASE_URL/v1/verification/code",
        "$BASE_URL/api/auth/verify-code",
        "$BASE_URL/auth/verify"
    )
    for (urlStr in endpoints) {
        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject()
                .put("phone", phone)
                .put("code", code)
                .toString()
            OutputStreamWriter(conn.outputStream).use { it.write(payload) }

            val codeHttp = conn.responseCode
            val stream = if (codeHttp in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
            conn.disconnect()

            if (codeHttp in 200..299) {
                val token = try {
                    val j = JSONObject(body)
                    j.optString("token", j.optString("accessToken", j.optString("session_token", "telefost_token_$phone")))
                } catch (_: Exception) {
                    "telefost_token_$phone"
                }
                return@withContext token
            }
        } catch (_: Exception) {
        }
    }
    if (expectedCode != null && code == expectedCode) {
        "local_session_${phone.hashCode()}"
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
    for (className in candidates) {
        try {
            val clazz = Class.forName(className)
            val intent = Intent(context, clazz).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
}
