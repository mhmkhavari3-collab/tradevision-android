package com.tradevision.network

import com.tradevision.BuildConfig
import com.tradevision.auth.AuthManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TradeVisionApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): ApiResponse
}

object ApiClient {
    private const val BASE_URL = "https://api.tradevision.app/"
    private var api: TradeVisionApi? = null
    fun getApi(): TradeVisionApi {
        if (api == null) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                    val token = try { AuthManager.getInstance().getAccessToken() } catch (e: Exception) { null }
                    token?.let { req.header("Authorization", "Bearer $it") }
                    chain.proceed(req.build())
                }
                .build()
            api = Retrofit.Builder().baseUrl(BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(TradeVisionApi::class.java)
        }
        return api!!
    }
    fun reset() { api = null }
}
