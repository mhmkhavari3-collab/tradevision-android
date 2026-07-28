package com.tradevision.network

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)
data class RefreshTokenRequest(val refreshToken: String)
data class AuthResponse(val success: Boolean, val data: AuthData?, val message: String? = null)
data class AuthData(val accessToken: String, val refreshToken: String, val expiresIn: Long, val tokenType: String = "Bearer", val user: User? = null)
data class User(val id: String, val email: String, val name: String, @SerializedName("avatar_url") val avatarUrl: String? = null, @SerializedName("created_at") val createdAt: String = "", @SerializedName("is_premium") val isPremium: Boolean = false)
data class ApiResponse(val success: Boolean, val message: String? = null, val data: Any? = null)
data class BatchTickerResponse(val success: Boolean, val data: Map<String, Any>? = null)
data class CandlesResponse(val success: Boolean, val data: List<Any> = emptyList())
data class PushRegistrationRequest(@SerializedName("fcm_token") val fcmToken: String, val platform: String = "android", @SerializedName("device_id") val deviceId: String, @SerializedName("device_model") val deviceModel: String? = null, @SerializedName("os_version") val osVersion: String? = null)
