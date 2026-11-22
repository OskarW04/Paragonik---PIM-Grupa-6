package com.example.paragonik

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paragonik.api.Api
import com.example.paragonik.api.PaymentRequestDto
import com.example.paragonik.api.userMessage
import kotlinx.coroutines.launch

class RequestsViewModel : ViewModel() {

    var requests by mutableStateOf<List<PaymentRequestDto>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var openedId by mutableStateOf<String?>(null) // rozwinięta prośba, np. ta z klikniętego powiadomienia

    fun load() {
        isLoading = true
        error = null
        viewModelScope.launch {
            try {
                requests = Api.service.incomingRequests()
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                isLoading = false
            }
        }
    }

    fun toggle(request: PaymentRequestDto) {
        openedId = if (openedId == request.id) null else request.id
    }
}
