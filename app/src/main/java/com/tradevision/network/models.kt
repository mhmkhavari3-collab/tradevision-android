package com.tradevision.network
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val success: Boolean, val data: AuthData?)
data class AuthData(val access_token: String, val refresh_token: String, val user: User?)
data class User(val id: String, val email: String, val name: String)
data class ApiResponse(val success: Boolean, val message: String? = null)
