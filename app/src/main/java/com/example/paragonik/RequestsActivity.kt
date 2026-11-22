package com.example.paragonik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.paragonik.ui.RequestsScreen
import com.example.paragonik.ui.theme.ParagonikTheme

// Otrzymane prośby o zwrot. Tu prowadzi też kliknięte powiadomienie.
class RequestsActivity : ComponentActivity() {

    private val viewModel: RequestsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.openedId = intent.getStringExtra(EXTRA_REQUEST_ID)
            viewModel.load()
        }
        setContent {
            ParagonikTheme {
                RequestsScreen(viewModel, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_REQUEST_ID = "requestId" // tak samo nazywa się pole w danych pusha
    }
}
