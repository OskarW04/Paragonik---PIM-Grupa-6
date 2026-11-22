package com.example.paragonik

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.paragonik.logic.Money
import com.example.paragonik.ui.AssignStep
import com.example.paragonik.ui.PeopleStep
import com.example.paragonik.ui.ReceiptStep
import com.example.paragonik.ui.SummaryStep
import com.example.paragonik.ui.theme.ParagonikTheme

// Rozliczenie w 4 krokach. Dostaje URI zdjęcia w intent.data (null = wpisywanie ręczne),
// a podsumowanie oddaje do MainActivity przez setResult.
class ReceiptActivity : ComponentActivity() {

    private val viewModel: ReceiptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.start(this, intent.data)
        setContent {
            ParagonikTheme {
                ReceiptFlow(viewModel, onClose = { finish() }, onSave = { saveAndFinish() })
            }
        }
    }

    private fun saveAndFinish() {
        val debts = viewModel.shares()
            .filter { it.person.id != viewModel.payerId && it.total != 0L }
            .joinToString(", ") {
                // ujemna kwota = to płacący oddaje tej osobie
                if (it.total > 0) {
                    "${it.person.name} ${Money.format(it.total)}"
                } else {
                    "${it.person.name} dostaje ${Money.format(-it.total)}"
                }
            }
        val result = Intent()
            .putExtra(EXTRA_STORE, viewModel.store.ifBlank { "Paragon" })
            .putExtra(EXTRA_DATE, viewModel.date)
            .putExtra(EXTRA_TOTAL, viewModel.itemsSum)
            .putExtra(EXTRA_PAYER, viewModel.payer.name)
            .putExtra(EXTRA_DEBTS, debts)
        setResult(RESULT_OK, result)
        finish()
    }

    companion object {
        // klucze wyniku dla MainActivity
        const val EXTRA_STORE = "store"
        const val EXTRA_DATE = "date"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_PAYER = "payer"
        const val EXTRA_DEBTS = "debts"
    }
}

// wspólny układ kroków: pasek z postępem, treść kroku i przyciski na dole
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptFlow(viewModel: ReceiptViewModel, onClose: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val step = viewModel.step
    var confirmClose by remember { mutableStateOf(false) }

    // na pierwszym kroku "wstecz" zamyka ekran, ale jak są już pozycje, to najpierw pytamy
    val goBack: () -> Unit = {
        if (!viewModel.back()) {
            if (viewModel.items.isEmpty()) {
                onClose()
            } else {
                confirmClose = true
            }
        }
    }
    BackHandler(onBack = goBack)

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Odrzucić paragon?") },
            text = { Text("Wprowadzone pozycje nie zostaną zapisane.") },
            confirmButton = { TextButton(onClick = onClose) { Text("Odrzuć") } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Anuluj") } },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Krok ${step.ordinal + 1} z 4: ${step.title}") },
                    navigationIcon = {
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (step.ordinal + 1) / Step.entries.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (step != Step.RECEIPT) {
                        OutlinedButton(onClick = goBack, modifier = Modifier.weight(1f)) { Text("Wstecz") }
                    }
                    Button(
                        onClick = {
                            if (step == Step.SUMMARY) {
                                onSave()
                            } else {
                                viewModel.next()?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            }
                        },
                        enabled = !viewModel.isReading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (step == Step.SUMMARY) "Zapisz rozliczenie" else "Dalej")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (step) {
                Step.RECEIPT -> ReceiptStep(viewModel)
                Step.PEOPLE -> PeopleStep(viewModel)
                Step.ASSIGN -> AssignStep(viewModel)
                Step.SUMMARY -> SummaryStep(viewModel)
            }
        }
    }
}
