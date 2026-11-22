package com.example.paragonik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.paragonik.RequestsViewModel
import com.example.paragonik.api.PaymentRequestDto
import com.example.paragonik.logic.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(vm: RequestsViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otrzymane prośby") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load() }, enabled = !vm.isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.isLoading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            vm.error?.let { message -> item { InfoBanner(message, warning = true) } }
            if (!vm.isLoading && vm.error == null && vm.requests.isEmpty()) {
                item {
                    Text(
                        "Nikt jeszcze nie prosił Cię o zwrot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.requests, key = { it.id }) { request ->
                RequestCard(request, expanded = request.id == vm.openedId, onClick = { vm.toggle(request) })
            }
        }
    }
}

@Composable
private fun RequestCard(request: PaymentRequestDto, expanded: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(request.sender.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(request.shopName, request.purchaseDate).joinToString(" · ").ifEmpty { "Paragon" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    Money.format(request.amount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // po kliknięciu pokazujemy, za co dokładnie jest ta kwota
            if (expanded && request.items.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                request.items.forEach { item ->
                    Row {
                        Text(item.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(Money.format(item.amount), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
