package com.tradevision.network

import com.tradevision.BuildConfig
import com.tradevision.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface TradeVisionApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") token: String): ApiResponse

    @GET("api/v1/ticker/batch")
    suspend fun getBatchTicker(@Header("Authorization") token: String, @Query("symbols") symbols: String): BatchTickerResponse

    @GET("api/v1/candles/{symbol}")
    suspend fun getCandles(@Header("Authorization") token: String, @Path("symbol") symbol: String, @Query("interval") interval: String = "1h", @Query("limit") limit: Int = 500): CandlesResponse

    @POST("api/v1/push/register")
    suspend fun registerPushToken(@Header("Authorization") token: String, @Body request: PushRegistrationRequest): ApiResponse
}

object ApiClient {
    private const val BASE_URL = "https://api.tradevision.app/"
    private const val BASE_URL_DEBUG = "http://10.0.2.2:8080/"
    private var retrofit: Retrofit? = null
    private var api: TradeVisionApi? = null

    private fun getBaseUrl(): String = if (BuildConfig.DEBUG) BASE_URL_DEBUG else BASE_URL

    fun getApi(): TradeVisionApi {
        if (api == null) {
            synchronized(this) {
                if (api == null) {
                    val httpClient = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .addInterceptor { chain ->
                            val original = chain.request()
                            val token = AuthManager.getInstance().getAccessToken()
                            val builder = original.newBuilder()
                                .header("User-Agent", "TradeVision-Android/${BuildConfig.VERSION_NAME}")
                            if (token != null) builder.header("Authorization", "Bearer $token")
                            chain.proceed(builder.build())
                        }
                        .build()
                    retrofit = Retrofit.Builder()
                        .baseUrl(getBaseUrl())
                        .client(httpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    api = retrofit!!.create(TradeVisionApi::class.java)
                }
            }
        }
        return api!!
    }

    fun reset() {
        synchronized(this) {
            retrofit = null
            api = null
        }
    }
}
