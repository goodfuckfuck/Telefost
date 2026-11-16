package com.telefost.messenger.data.remote

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.converter.kotlinx.serialization.serialization

@Serializable
data class PhoneRequest(val phone: String)

@Serializable
data class VerifyRequest(val phone: String, val code: String)

@Serializable
data class AuthResponse(val token: String?, val message: String?)

interface TelefostApiService {
    @POST("v1/auth/request-code")
    suspend fun requestCode(@Body request: PhoneRequest): Response<AuthResponse>

    @POST("v1/auth/verify")
    suspend fun verifyCode(@Body request: VerifyRequest): Response<AuthResponse>

    companion object {
        private const val BASE_URL = "https://telefost-api.onrender.com/"

        val instance: TelefostApiService by lazy {
            val json = Json { ignoreUnknownKeys = true }
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(TelefostApiService::class.java)
        }
    }
}
