package com.example.paragonik

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paragonik.api.Api
import com.example.paragonik.api.LoginRequest
import com.example.paragonik.api.RegisterRequest
import com.example.paragonik.api.Session
import com.example.paragonik.api.userMessage
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {

    var isRegister by mutableStateOf(false)
        private set
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var displayName by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun switchMode() {
        isRegister = !isRegister
        error = null
    }

    // Po udanym logowaniu Session.user przestaje być null i MainActivity sama pokazuje ekran główny.
    fun submit(context: Context) {
        val appContext = context.applicationContext
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                val auth = if (isRegister) {
                    Api.service.register(RegisterRequest(email.trim(), password, displayName.trim()))
                } else {
                    Api.service.login(LoginRequest(email.trim(), password))
                }
                Session.save(appContext, auth)
                password = ""
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                isLoading = false
            }
        }
    }
}
