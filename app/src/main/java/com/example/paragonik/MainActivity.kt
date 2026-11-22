package com.example.paragonik

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import com.example.paragonik.api.Session
import com.example.paragonik.push.FcmTokenWorker
import com.example.paragonik.ui.HomeScreen
import com.example.paragonik.ui.LoginScreen
import com.example.paragonik.ui.theme.ParagonikTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

data class HistoryEntry(
    val store: String,
    val date: String,
    val total: Long,
    val payer: String,
    val debts: String,
)

// Na razie historia jest tylko w pamięci i znika po zamknięciu aplikacji. Docelowo Room.
object History {
    val entries = mutableStateListOf<HistoryEntry>()
}

// Ekran główny, a bez zalogowania ekran logowania. Zdjęcie paragonu idzie do ReceiptActivity
// w intencji, a gotowe rozliczenie wraca przez Activity Result.
class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()

    // skaner ML Kit sam wykrywa krawędzie, przycina i pozwala powtórzyć zdjęcie
    private val scanner = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scan?.pages?.firstOrNull()?.imageUri?.let { openReceipt(it) }
        }
    }

    // zwykły wybór zdjęcia, gdyby skaner nie działał
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) openReceipt(uri)
    }

    private val receiptScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            History.entries.add(
                0,
                HistoryEntry(
                    store = data.getStringExtra(ReceiptActivity.EXTRA_STORE).orEmpty(),
                    date = data.getStringExtra(ReceiptActivity.EXTRA_DATE).orEmpty(),
                    total = data.getLongExtra(ReceiptActivity.EXTRA_TOTAL, 0L),
                    payer = data.getStringExtra(ReceiptActivity.EXTRA_PAYER).orEmpty(),
                    debts = data.getStringExtra(ReceiptActivity.EXTRA_DEBTS).orEmpty(),
                ),
            )
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequestFromPush(intent)
        setContent {
            ParagonikTheme {
                val user = Session.user
                if (user == null) {
                    LoginScreen(loginViewModel)
                } else {
                    // po zalogowaniu (i przy każdym starcie) dajemy serwerowi token FCM tego telefonu
                    LaunchedEffect(user.id) {
                        FcmTokenWorker.enqueue(this@MainActivity)
                        askForNotifications()
                    }
                    HomeScreen(
                        history = History.entries,
                        onScan = { startScanner() },
                        onPickPhoto = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onManual = { openReceipt(null) },
                        onDelete = { History.entries.remove(it) },
                        onRequests = { openRequests(null) },
                        onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequestFromPush(intent)
    }

    // Kliknięte powiadomienie, gdy aplikacja była w tle: system otwiera tę aktywność z danymi pusha.
    private fun openRequestFromPush(intent: Intent) {
        val requestId = intent.getStringExtra(RequestsActivity.EXTRA_REQUEST_ID) ?: return
        if (Session.user != null) openRequests(requestId)
    }

    private fun openRequests(requestId: String?) {
        startActivity(Intent(this, RequestsActivity::class.java).putExtra(RequestsActivity.EXTRA_REQUEST_ID, requestId))
    }

    // od Androida 13 trzeba zapytać o zgodę, inaczej pushe się nie pokażą
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener(this) { sender -> scanner.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener(this) {
                Toast.makeText(this, "Skaner jest niedostępny. Wybierz zdjęcie z galerii.", Toast.LENGTH_LONG).show()
            }
    }

    // imageUri == null -> wpisywanie ręczne
    private fun openReceipt(imageUri: Uri?) {
        val intent = Intent(this, ReceiptActivity::class.java)
        if (imageUri != null) {
            intent.data = imageUri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        receiptScreen.launch(intent)
    }
}
