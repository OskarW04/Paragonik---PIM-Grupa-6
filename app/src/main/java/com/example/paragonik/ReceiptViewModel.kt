package com.example.paragonik

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paragonik.api.Api
import com.example.paragonik.api.CreatePaymentRequest
import com.example.paragonik.api.PaymentItemDto
import com.example.paragonik.api.UserDto
import com.example.paragonik.api.userMessage
import com.example.paragonik.logic.PersonShare
import com.example.paragonik.logic.Share
import com.example.paragonik.logic.SplitCalculator
import com.example.paragonik.model.Person
import com.example.paragonik.model.ReceiptItem
import com.example.paragonik.ocr.ReceiptOcr
import com.example.paragonik.ocr.ReceiptParser
import com.example.paragonik.push.SendRequestWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

enum class Step(val title: String) {
    RECEIPT("Paragon"),
    PEOPLE("Osoby"),
    ASSIGN("Przypisanie"),
    SUMMARY("Podsumowanie"),
}

// Stan jednego rozliczenia. Siedzi w ViewModelu, więc przeżywa odtworzenie aktywności.
class ReceiptViewModel : ViewModel() {

    var step by mutableStateOf(Step.RECEIPT)
        private set

    // krok 1: paragon
    var isReading by mutableStateOf(false)
        private set
    var readError by mutableStateOf<String?>(null)
        private set
    var photo by mutableStateOf<ImageBitmap?>(null)
        private set
    var ocrText by mutableStateOf("")
        private set
    var store by mutableStateOf("")
    var date by mutableStateOf("")
    var total by mutableStateOf<Long?>(null) // suma z paragonu, null = nie odczytano
    val items = mutableStateListOf<ReceiptItem>()

    // krok 2: osoby
    val people = mutableStateListOf(Person(id = 0, name = "Ja", isMe = true))
    var payerId by mutableStateOf(0L)
        private set
    var guestName by mutableStateOf("")
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<UserDto>>(emptyList())
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    // krok 3: id pozycji -> id osób. Brak wpisu = pozycja wspólna dla wszystkich.
    val assigned = mutableStateMapOf<Long, Set<Long>>()

    // krok 4
    var requestsSent by mutableStateOf(false)
        private set

    private var nextId = 1L
    private var started = false

    val itemsSum: Long get() = items.sumOf { it.price }

    val payer: Person get() = people.firstOrNull { it.id == payerId } ?: people.first()

    // imageUri == null to wpisywanie ręczne
    fun start(context: Context, imageUri: Uri?) {
        if (started) return // onCreate woła to też po odtworzeniu aktywności
        started = true
        if (imageUri == null) return

        val appContext = context.applicationContext
        isReading = true
        viewModelScope.launch {
            try {
                photo = withContext(Dispatchers.IO) { loadPhoto(appContext, imageUri) }
                val lines = ReceiptOcr.recognize(appContext, imageUri)
                val rows = ReceiptParser.groupIntoRows(lines)
                val receipt = ReceiptParser.parseRows(rows)
                ocrText = rows.joinToString("\n")
                store = receipt.store
                date = receipt.date
                total = receipt.total
                receipt.items.forEach { addItem(it) }
                if (receipt.items.isEmpty()) {
                    readError = "Nie znaleziono pozycji na zdjęciu. Dodaj je ręcznie albo zrób zdjęcie jeszcze raz."
                }
            } catch (e: Exception) {
                readError = "Nie udało się odczytać paragonu (${e.message}). Możesz wpisać pozycje ręcznie."
            } finally {
                isReading = false
            }
        }
    }

    fun addItem(item: ReceiptItem) {
        items.add(item.copy(id = nextId++))
    }

    fun updateItem(item: ReceiptItem) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) items[index] = item
    }

    fun removeItem(item: ReceiptItem) {
        items.removeAll { it.id == item.id }
        assigned.remove(item.id)
    }

    fun addGuest() {
        addPerson(guestName)
        guestName = ""
    }

    fun addPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        people.add(Person(id = nextId++, name = trimmed))
    }

    // Szuka osób z kontem na serwerze. Serwer odpowiada dopiero od 2 znaków.
    fun search(query: String) {
        searchQuery = query
        searchJob?.cancel()
        searchError = null
        if (query.trim().length < 2) {
            searchResults = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // czekamy, aż użytkownik skończy pisać, zamiast pytać serwer o każdą literę
            try {
                searchResults = Api.service.searchUsers(query.trim())
            } catch (e: CancellationException) {
                throw e // to nie błąd, tylko kolejne wpisanie anulowało to wyszukiwanie
            } catch (e: Exception) {
                searchError = e.userMessage()
            }
        }
    }

    fun addAccount(user: UserDto) {
        if (people.none { it.accountId == user.id }) {
            people.add(Person(id = nextId++, name = user.displayName, accountId = user.id))
        }
        searchJob?.cancel()
        searchQuery = ""
        searchResults = emptyList()
    }

    fun removePerson(person: Person) {
        if (person.isMe) return
        people.removeAll { it.id == person.id }
        for ((itemId, ids) in assigned.toMap()) {
            val rest = ids - person.id
            // jak przy pozycji nikt nie został, wraca do wspólnych
            if (rest.isEmpty()) assigned.remove(itemId) else assigned[itemId] = rest
        }
        if (payerId == person.id) payerId = people.first().id
    }

    fun selectPayer(person: Person) {
        payerId = person.id
    }

    fun isAssigned(item: ReceiptItem, person: Person): Boolean =
        assigned[item.id]?.contains(person.id) ?: true

    // Zwraca false, jeśli pozycja zostałaby bez nikogo.
    fun toggle(item: ReceiptItem, person: Person): Boolean {
        val everyone = people.map { it.id }.toSet()
        val ids = assigned[item.id] ?: everyone
        val newIds = if (person.id in ids) ids - person.id else ids + person.id
        if (newIds.isEmpty()) return false
        // zaznaczeni wszyscy = znowu pozycja wspólna
        if (newIds == everyone) assigned.remove(item.id) else assigned[item.id] = newIds
        return true
    }

    fun shares(): List<PersonShare> = SplitCalculator.split(items, people, assigned)

    // Osoby z kontem, które mają coś do oddania. Prośby wysyła tylko ten, kto płacił, czyli ja.
    fun debtorsWithAccount(): List<PersonShare> =
        if (payer.isMe) shares().filter { it.person.accountId != null && it.total > 0 } else emptyList()

    fun paymentRequests(): List<CreatePaymentRequest> {
        val purchaseDate = date.trim().takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
        val requests = mutableListOf<CreatePaymentRequest>()
        for (share in debtorsWithAccount()) {
            val accountId = share.person.accountId ?: continue
            // serwer nie przyjmie ujemnych pozycji ani ponad 200 sztuk, a lista pozycji jest opcjonalna
            val items = if (share.shares.none { it.amount < 0 } && share.shares.size <= 200) {
                share.shares.filter { it.amount > 0 }.map { PaymentItemDto(itemName(it), it.amount) }
            } else {
                emptyList()
            }
            requests.add(
                CreatePaymentRequest(
                    recipientId = accountId,
                    amount = share.total,
                    shopName = store.trim().take(100).ifEmpty { null },
                    purchaseDate = purchaseDate,
                    items = items,
                    clientId = UUID.randomUUID().toString(),
                ),
            )
        }
        return requests
    }

    // Prośby idą przez kolejkę WorkManagera, więc bez internetu poczekają na połączenie.
    fun sendRequests(context: Context) {
        for (request in paymentRequests()) SendRequestWorker.enqueue(context, request)
        requestsSent = true
    }

    // "Płyn do naczyń (1/2)", gdy pozycja jest dzielona
    private fun itemName(share: Share): String {
        val name = share.item.name.trim().take(190)
        return if (share.splitBetween > 1) "$name (1/${share.splitBetween})" else name
    }

    // Zwraca komunikat, jeśli nie można przejść dalej.
    fun next(): String? {
        if (step == Step.PEOPLE) addGuest() // imię wpisane, ale niedodane przyciskiem
        when (step) {
            Step.RECEIPT -> {
                if (items.isEmpty()) return "Dodaj co najmniej jedną pozycję."
                val receiptTotal = total
                if (receiptTotal != null && receiptTotal != itemsSum) {
                    return "Suma pozycji nie zgadza się z paragonem. Popraw pozycje albo sumę."
                }
            }
            Step.PEOPLE -> if (people.size < 2) return "Dodaj co najmniej jedną osobę oprócz siebie."
            Step.ASSIGN, Step.SUMMARY -> Unit
        }
        if (step == Step.SUMMARY) return null
        step = Step.entries[step.ordinal + 1]
        return null
    }

    // false = jesteśmy już na pierwszym kroku
    fun back(): Boolean {
        if (step == Step.RECEIPT) return false
        step = Step.entries[step.ordinal - 1]
        return true
    }

    // Do podglądu wystarczy ok. 1600 px na dłuższym boku, pełne zdjęcie zajęłoby za dużo pamięci.
    private fun loadPhoto(context: Context, uri: Uri): ImageBitmap? = try {
        val maxSide = 1600
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longer = maxOf(info.size.width, info.size.height)
                if (longer > maxSide) decoder.setTargetSampleSize((longer + maxSide - 1) / maxSide)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longer = maxOf(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, (longer + maxSide - 1) / maxSide) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
