package com.example.paragonik

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.example.paragonik.api.Session
import com.example.paragonik.ui.SettingsScreen
import com.example.paragonik.ui.theme.ParagonikTheme

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParagonikTheme {
                // po wylogowaniu albo usunięciu konta wracamy do MainActivity, która pokaże logowanie
                LaunchedEffect(Session.user) {
                    if (Session.user == null) finish()
                }
                SettingsScreen(viewModel, onBack = { finish() })
            }
        }
    }
}
