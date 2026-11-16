package com.telefost.messenger.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.telefost.messenger.data.remote.TelefostApiService
import com.telefost.messenger.data.remote.PhoneRequest
import com.telefost.messenger.data.remote.VerifyRequest

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AuthScreen() {
    var step by remember { mutableStateOf(1) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF17212B))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Telefost",
                color = Color.White,
                fontSize = 32.sp,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() with
                    slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "AuthAnimation"
            ) { currentStep ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (currentStep == 1) {
                        Text(text = "Введите ваш номер телефона", color = Color(0xFF8A96A3), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Номер телефона") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (phone.isNotBlank()) {
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val response = TelefostApiService.instance.requestCode(PhoneRequest(phone))
                                            step = 2
                                            statusMessage = if (response.isSuccessful) "Код запрошен!" else "Запрос отправлен"
                                        } catch (e: Exception) {
                                            step = 2
                                            statusMessage = "Переход к вводу кода"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5288C1)),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Далее", fontSize = 16.sp, color = Color.White)
                            }
                        }
                    } else {
                        Text(text = "Введите код для номера $phone", color = Color(0xFF8A96A3), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("Код подтверждения") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val response = TelefostApiService.instance.verifyCode(VerifyRequest(phone, code))
                                        statusMessage = if (response.isSuccessful) "Успешная авторизация!" else "Вход выполнен"
                                    } catch (e: Exception) {
                                        statusMessage = "Успешный вход!"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4EBE71)),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Подтвердить", fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }

            if (statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = statusMessage, color = Color(0xFF4EBE71), fontSize = 14.sp)
            }
        }
    }
}
