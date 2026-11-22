package com.example.paragonik

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.example.paragonik.api.Api
import com.example.paragonik.api.LogoutRequest
import com.example.paragonik.api.Session
import com.example.paragonik.api.UpdateProfileRequest
import com.example.paragonik.api.userMessage
import com.example.paragonik.push.SendRequestWorker
import com.example.paragonik.push.currentFcmToken
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    var displayName by mutableStateOf(Session.user?.displayName.orEmpty())
    var isBusy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun saveName(context: Context) {
        val appContext = context.applicationContext
        isBusy = true
        error = null
        viewModelScope.launch {
            try {
                val user = Api.service.updateProfile(UpdateProfileRequest(displayName.trim()))
                Session.updateUser(appContext, user)
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                isBusy = false
            }
        }
    }

    fun clearHistory() {
        History.entries.clear()
    }

    fun logout(context: Context) {
        val appContext = context.applicationContext
        isBusy = true
        viewModelScope.launch {
            try {
                // serwer zapomina token tego telefonu, żeby nie przychodziły tu pushe wylogowanego konta
                Api.service.logout(LogoutRequest(fcmToken = currentFcmToken()))
            } catch (e: Exception) {
                // bez internetu i tak wylogowujemy na telefonie
            }
            signOut(appContext)
        }
    }

    fun deleteAccount(context: Context) {
        val appContext = context.applicationContext
        isBusy = true
        error = null
        viewModelScope.launch {
            try {
                Api.service.deleteAccount()
                signOut(appContext)
            } catch (e: Exception) {
                error = e.userMessage()
                isBusy = false
            }
        }
    }

    // niewysłane prośby należały do tego konta, więc też je kasujemy
    private suspend fun signOut(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(SendRequestWorker.TAG)
        Session.clear(context)
    }
}
