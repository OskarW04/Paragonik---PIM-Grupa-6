package com.example.paragonik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.paragonik.ReceiptViewModel
import com.example.paragonik.logic.Money
import com.example.paragonik.logic.PersonShare
import com.example.paragonik.model.Person
import kotlin.math.abs

// Krok 4: kto ile oddaje osobie, która płaciła.
@Composable
fun SummaryStep(vm: ReceiptViewModel) {
    val context = LocalContext.current
    val shares = vm.shares()
    val payer = vm.payer
    val sharesSum = shares.sumOf { it.total }
    val payerShare = shares.firstOrNull { it.person.id == payer.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        listOf(vm.store.ifBlank { "Paragon" }, vm.date).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Razem: ${Money.format(vm.itemsSum)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (payer.isMe) "Płacisz Ty" else "Płaci: ${payer.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        items(shares.filter { it.person.id != payer.id }, key = { it.person.id }) { share ->
            DebtCard(share, payer)
        }

        if (payerShare != null) {
            item {
                Text(
                    (if (payer.isMe) "Twój udział" else "Udział osoby płacącej (${payer.name})") +
                        ": ${Money.format(payerShare.total)} — nie tworzy długu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // porównujemy z sumą z paragonu, a jak jej nie odczytano, to z sumą pozycji
        item {
            val receiptTotal = vm.total ?: vm.itemsSum
            val ok = sharesSum == receiptTotal
            InfoBanner(
                if (ok) {
                    "Kontrola: suma udziałów ${Money.format(sharesSum)} = suma paragonu."
                } else {
                    "Uwaga: suma udziałów ${Money.format(sharesSum)} ≠ suma paragonu ${Money.format(receiptTotal)}."
                },
                warning = !ok,
            )
        }

        item {
            val debtors = vm.debtorsWithAccount()
            when {
                vm.requestsSent -> InfoBanner(
                    "Prośby o zwrot wysłane. Jeśli nie ma internetu, wyjdą, gdy wróci połączenie.",
                    warning = false,
                )
                debtors.isNotEmpty() -> Button(onClick = { vm.sendRequests(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Wyślij prośby o zwrot (${debtors.size})")
                }
                !payer.isMe -> Text(
                    "Prośby o zwrot wysyła osoba, która płaciła.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Goście nie dostają powiadomień, swoją kwotę widzą na tym ekranie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DebtCard(share: PersonShare, payer: Person) {
    val direction = if (share.total >= 0) {
        when {
            share.person.isMe -> "oddajesz osobie: ${payer.name}"
            payer.isMe -> "oddaje Tobie"
            else -> "oddaje osobie: ${payer.name}"
        }
    } else {
        // ujemny bilans (np. przez zwrot kaucji): to płacący oddaje tej osobie
        when {
            share.person.isMe -> "dostajesz od: ${payer.name}"
            payer.isMe -> "dostaje od Ciebie"
            else -> "dostaje od: ${payer.name}"
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(share.person)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(share.person.name, style = MaterialTheme.typography.titleMedium)
                    Text(direction, style = MaterialTheme.typography.bodySmall)
                    if (!share.person.isMe) {
                        Text(
                            if (share.person.accountId != null) "konto" else "gość · bez powiadomienia",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    Money.format(abs(share.total)),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (share.shares.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                share.shares.forEach { part ->
                    Row {
                        Text(
                            part.item.name + if (part.splitBetween > 1) " (dzielone na ${part.splitBetween})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Money.format(part.amount), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
