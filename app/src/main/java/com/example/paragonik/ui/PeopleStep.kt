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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.paragonik.ReceiptViewModel
import com.example.paragonik.api.UserDto
import com.example.paragonik.model.Person

// Krok 2: osoby z kontem z wyszukiwarki, goście i kto zapłacił.
@Composable
fun PeopleStep(vm: ReceiptViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Kto bierze udział w rozliczeniu?", style = MaterialTheme.typography.titleMedium) }
        item {
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.search(it) },
                label = { Text("Szukaj osoby z kontem (imię lub e-mail)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        vm.searchError?.let { message ->
            item {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        items(vm.searchResults, key = { "found-${it.id}" }) { user ->
            FoundUserRow(user, onAdd = { vm.addAccount(user) })
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = vm.guestName,
                    onValueChange = { vm.guestName = it },
                    label = { Text("Imię gościa (bez konta)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { vm.addGuest() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { vm.addGuest() }, enabled = vm.guestName.isNotBlank()) { Text("Dodaj") }
            }
        }
        item {
            Text(
                "Osoby z kontem dostaną prośbę o zwrot. Goście nie dostają powiadomień, swoją kwotę zobaczą na tym telefonie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                "Kto zapłacił? Zaznacz jedną osobę.",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(vm.people, key = { it.id }) { person ->
            PersonRow(
                person = person,
                isPayer = person.id == vm.payerId,
                onSelect = { vm.selectPayer(person) },
                onRemove = { vm.removePerson(person) },
            )
        }
    }
}

@Composable
private fun FoundUserRow(user: UserDto, onAdd: () -> Unit) {
    Card(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.Add, contentDescription = "Dodaj")
        }
    }
}

@Composable
private fun PersonRow(person: Person, isPayer: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val kind = if (person.accountId != null) "konto" else "gość"
    Card(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isPayer, onClick = onSelect)
            Avatar(person)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(person.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        person.isMe && isPayer -> "to Ty · płacisz za paragon"
                        person.isMe -> "to Ty"
                        isPayer -> "$kind · płaci za paragon"
                        else -> kind
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!person.isMe) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Usuń osobę")
                }
            }
        }
    }
}
