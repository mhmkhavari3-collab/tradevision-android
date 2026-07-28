package com.tradevision.auth

import android.content.Context
import android.content.SharedPreferences

class AuthManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: AuthManager? = null

        fun initialize(ctx: Context) {
            if (instance == null) synchronized(this) {
                if (instance == null) instance = AuthManager(ctx.applicationContext)
            }
        }

        fun getInstance(): AuthManager =
            instance ?: throw IllegalStateException("AuthManager not initialized")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun isLoggedIn(): Boolean =
        prefs.getBoolean("is_logged_in", false) && getAccessToken() != null

    fun getUserId(): String? = prefs.getString("user_id", null)

    fun setAuthData(access: String, refresh: String, userId: String? = null, email: String? = null) {
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .putBoolean("is_logged_in", true)
            .apply()
        userId?.let { prefs.edit().putString("user_id", it).apply() }
        email?.let { prefs.edit().putString("user_email", it).apply() }
    }

    fun clearTokens() {
        prefs.edit().clear().putBoolean("is_logged_in", false).apply()
    }
}
