package com.example.paragonik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.paragonik.LoginViewModel

@Composable
fun LoginScreen(vm: LoginViewModel) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Paragonik", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Zaloguj się, żeby wysyłać znajomym prośby o zwrot i dostawać ich prośby.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (vm.isRegister) {
                OutlinedTextField(
                    value = vm.displayName,
                    onValueChange = { vm.displayName = it },
                    label = { Text("Imię (widzą je inni)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = vm.email,
                onValueChange = { vm.email = it },
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.password,
                onValueChange = { vm.password = it },
                label = { Text(if (vm.isRegister) "Hasło (min. 8 znaków)" else "Hasło") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.error?.let { InfoBanner(it, warning = true) }
            Button(onClick = { vm.submit(context) }, enabled = !vm.isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.isRegister) "Załóż konto" else "Zaloguj się")
            }
            TextButton(onClick = { vm.switchMode() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.isRegister) "Mam już konto" else "Nie mam konta")
            }
        }
    }
}
