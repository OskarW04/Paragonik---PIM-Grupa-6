package com.example.paragonik.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.paragonik.ReceiptViewModel
import com.example.paragonik.logic.Money
import com.example.paragonik.model.ReceiptItem
import com.example.paragonik.ocr.ReceiptParser

// Krok 1: sprawdzenie i poprawki tego, co odczytał OCR.
@Composable
fun ReceiptStep(vm: ReceiptViewModel) {
    var editedItem by remember { mutableStateOf<ReceiptItem?>(null) }
    var addingItem by remember { mutableStateOf(false) }
    var editingHeader by remember { mutableStateOf(false) }
    var showPhoto by remember { mutableStateOf(false) }
    var showOcrText by remember { mutableStateOf(false) }

    if (vm.isReading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Odczytuję paragon…", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        vm.readError?.let { message -> item { InfoBanner(message, warning = true) } }
        item { HeaderCard(vm, onEdit = { editingHeader = true }, onShowPhoto = { showPhoto = true }) }
        item { SumCheck(vm) }
        if (vm.items.any { !ReceiptParser.isConsistent(it) }) {
            item {
                InfoBanner(
                    "Przy pozycjach oznaczonych na czerwono ilość × cena nie daje wartości. Porównaj je ze zdjęciem.",
                    warning = true,
                )
            }
        }
        items(vm.items, key = { it.id }) { receiptItem ->
            ItemCard(receiptItem, onClick = { editedItem = receiptItem }, onDelete = { vm.removeItem(receiptItem) })
        }
        item {
            OutlinedButton(onClick = { addingItem = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Dodaj pozycję")
            }
        }
        if (vm.ocrText.isNotEmpty()) {
            item {
                TextButton(onClick = { showOcrText = !showOcrText }) {
                    Text(if (showOcrText) "Ukryj tekst z OCR" else "Pokaż tekst odczytany ze zdjęcia")
                }
            }
            if (showOcrText) {
                item {
                    Text(vm.ocrText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    editedItem?.let { item ->
        ItemDialog(initial = item, onDismiss = { editedItem = null }, onSave = { vm.updateItem(it); editedItem = null })
    }
    if (addingItem) {
        ItemDialog(initial = null, onDismiss = { addingItem = false }, onSave = { vm.addItem(it); addingItem = false })
    }
    if (editingHeader) HeaderDialog(vm, onDismiss = { editingHeader = false })
    val photo = vm.photo
    if (showPhoto && photo != null) PhotoDialog(photo, onDismiss = { showPhoto = false })
}

@Composable
private fun HeaderCard(vm: ReceiptViewModel, onEdit: () -> Unit, onShowPhoto: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = vm.photo
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = "Zdjęcie paragonu — dotknij, aby powiększyć",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowPhoto),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(vm.store.ifBlank { "Sklep — uzupełnij" }, style = MaterialTheme.typography.titleMedium)
                Text(vm.date.ifBlank { "Data — uzupełnij" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Suma z paragonu: " + (vm.total?.let { Money.format(it) } ?: "—"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.Filled.Edit, contentDescription = "Edytuj sklep, datę i sumę")
        }
    }
}

// czy suma pozycji zgadza się z sumą z paragonu
@Composable
private fun SumCheck(vm: ReceiptViewModel) {
    if (vm.items.isEmpty()) return
    val sum = vm.itemsSum
    val total = vm.total
    when {
        total == null -> InfoBanner("Nie odczytano sumy z paragonu. Suma pozycji: ${Money.format(sum)}.", warning = true)
        total != sum -> InfoBanner(
            "Suma pozycji (${Money.format(sum)}) różni się od sumy z paragonu (${Money.format(total)}). " +
                "Porównaj pozycje ze zdjęciem.",
            warning = true,
        )
        else -> InfoBanner("Suma pozycji zgadza się z paragonem: ${Money.format(sum)}", warning = false)
    }
}

@Composable
private fun ItemCard(item: ReceiptItem, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (item.unitPrice != 0L) {
                    val ok = ReceiptParser.isConsistent(item)
                    Text(
                        "${item.quantity} × ${Money.format(item.unitPrice)}" + if (ok) "" else " · nie zgadza się z wartością",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                if (item.discount != 0L) {
                    Text(
                        "w tym rabat ${Money.format(item.discount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(Money.format(item.price), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Usuń pozycję")
            }
        }
    }
}

// initial == null -> dodawanie nowej pozycji
@Composable
private fun ItemDialog(initial: ReceiptItem?, onDismiss: () -> Unit, onSave: (ReceiptItem) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var quantity by remember { mutableStateOf(initial?.quantity ?: "1") }
    var unitPrice by remember { mutableStateOf(initial?.let { Money.formatPlain(it.unitPrice) } ?: "") }
    var price by remember { mutableStateOf(initial?.let { Money.formatPlain(it.price) } ?: "") }

    val priceValue = Money.parse(price)
    // bez ceny jedn.: przy ilości 1 to po prostu wartość, a inaczej zostawiamy 0
    val unitValue = when {
        unitPrice.isNotBlank() -> Money.parse(unitPrice)
        quantity.trim() in listOf("", "1") -> priceValue
        else -> 0L
    }
    val canSave = name.isNotBlank() && priceValue != null && unitValue != null
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nowa pozycja" else "Edytuj pozycję") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Ilość") },
                        singleLine = true,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unitPrice,
                        onValueChange = { unitPrice = it },
                        label = { Text("Cena jedn.") },
                        singleLine = true,
                        isError = unitPrice.isNotBlank() && Money.parse(unitPrice) == null,
                        keyboardOptions = decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Wartość (zł)") },
                    singleLine = true,
                    isError = price.isNotBlank() && priceValue == null,
                    supportingText = { Text("Ta kwota jest dzielona między osoby") },
                    keyboardOptions = decimal,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        ReceiptItem(
                            id = initial?.id ?: 0,
                            name = name.trim(),
                            quantity = quantity.trim().ifEmpty { "1" },
                            unitPrice = unitValue ?: 0L,
                            price = priceValue ?: 0L,
                            discount = initial?.discount ?: 0L,
                        ),
                    )
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun HeaderDialog(vm: ReceiptViewModel, onDismiss: () -> Unit) {
    var store by remember { mutableStateOf(vm.store) }
    var date by remember { mutableStateOf(vm.date) }
    var total by remember { mutableStateOf(vm.total?.let { Money.formatPlain(it) } ?: "") }
    val totalValue = Money.parse(total)
    val totalOk = total.isBlank() || totalValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dane paragonu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = store, onValueChange = { store = it }, label = { Text("Sklep") }, singleLine = true)
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data (RRRR-MM-DD)") }, singleLine = true)
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it },
                    label = { Text("Suma z paragonu (zł)") },
                    singleLine = true,
                    isError = !totalOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = totalOk,
                onClick = {
                    vm.store = store.trim()
                    vm.date = date.trim()
                    vm.total = if (total.isBlank()) null else totalValue
                    onDismiss()
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// zdjęcie na cały ekran, powiększanie dwoma palcami
@Composable
private fun PhotoDialog(photo: ImageBitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                },
        ) {
            Image(
                bitmap = photo,
                contentDescription = "Zdjęcie paragonu",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Zamknij", tint = Color.White)
            }
        }
    }
}
