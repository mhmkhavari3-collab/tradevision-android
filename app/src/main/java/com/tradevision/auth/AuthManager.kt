package com.tradevision.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tradevision.network.ApiClient
import com.tradevision.network.RefreshTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val TOKEN_REFRESH_BUFFER_MS = 60_000L

        @Volatile
        private var instance: AuthManager? = null

        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = AuthManager(context.applicationContext)
                    }
                }
            }
        }

        fun getInstance(): AuthManager {
            return instance ?: throw IllegalStateException(
                "AuthManager not initialized. Call AuthManager.initialize(context) first."
            )
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val listeners = mutableListOf<AuthStateListener>()

    interface AuthStateListener {
        fun onLoggedIn(userId: String, email: String)
        fun onLoggedOut()
        fun onTokenRefreshed()
        fun onTokenRefreshFailed()
    }

    fun addListener(listener: AuthStateListener) { listeners.add(listener) }
    fun removeListener(listener: AuthStateListener) { listeners.remove(listener) }

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        if (token != null && expiry > 0 && isTokenExpired(expiry)) {
            Log.d(TAG, "Access token expired")
            return null
        }
        return token
    }

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    suspend fun setTokens(accessToken: String, refreshToken: String) = withContext(Dispatchers.IO) {
        val expiry = decodeTokenExpiry(accessToken)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiry)
            .apply()
    }

    suspend fun setAuthData(accessToken: String, refreshToken: String, expiresIn: Long, userId: String? = null, email: String? = null, name: String? = null) = withContext(Dispatchers.IO) {
        val expiry = System.currentTimeMillis() + (expiresIn * 1000)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiry)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
        userId?.let { prefs.edit().putString(KEY_USER_ID, it).apply() }
        email?.let { prefs.edit().putString(KEY_USER_EMAIL, it).apply() }
        name?.let { prefs.edit().putString(KEY_USER_NAME, it).apply() }
        listeners.forEach { it.onLoggedIn(userId ?: "", email ?: "") }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false) && getAccessToken() != null
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)

    suspend fun refreshToken(): String? = withContext(Dispatchers.IO) {
        val refreshToken = getRefreshToken() ?: run {
            listeners.forEach { it.onTokenRefreshFailed() }
            return@withContext null
        }
        try {
            val response = ApiClient.getApi().refreshToken(RefreshTokenRequest(refreshToken))
            if (response.success && response.data != null) {
                setTokens(response.data.accessToken, response.data.refreshToken)
                response.data.user?.let { user ->
                    prefs.edit()
                        .putString(KEY_USER_ID, user.id)
                        .putString(KEY_USER_EMAIL, user.email)
                        .putString(KEY_USER_NAME, user.name)
                        .apply()
                }
                listeners.forEach { it.onTokenRefreshed() }
                return@withContext response.data.accessToken
            } else {
                listeners.forEach { it.onTokenRefreshFailed() }
                return@withContext null
            }
        } catch (e: Exception) {
            listeners.forEach { it.onTokenRefreshFailed() }
            return@withContext null
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            if (token != null) {
                ApiClient.getApi().logout("Bearer $token")
            }
        } catch (e: Exception) { /* ignore */ }
        finally {
            clearTokens()
            ApiClient.reset()
            listeners.forEach { it.onLoggedOut() }
        }
    }

    suspend fun clearTokens() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
    }

    private fun isTokenExpired(expiry: Long): Boolean = System.currentTimeMillis() >= expiry
    private fun isTokenExpiringSoon(): Boolean {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() >= (expiry - TOKEN_REFRESH_BUFFER_MS)
    }

    private fun decodeTokenExpiry(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                val json = com.google.gson.JsonParser.parseString(payload).asJsonObject
                val exp = json.get("exp")?.asLong
                if (exp != null) exp * 1000 else System.currentTimeMillis() + 3600_000
            } else {
                System.currentTimeMillis() + 3600_000
            }
        } catch (e: Exception) { System.currentTimeMillis() + 3600_000 }
    }
}
