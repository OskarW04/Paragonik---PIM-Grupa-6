# Paragonik — specyfikacja projektu

## Opis aplikacji

Paragonik zamienia jedno zdjęcie paragonu w gotowe rozliczenie: kto, komu i ile oddaje. Aplikacja odczytuje z paragonu produkty, ilości i ceny, pozwala przypisać każdy produkt do jednej lub kilku osób, wylicza długi wobec osoby, która płaciła, i wysyła uczestnikom z kontem powiadomienie z prośbą o zwrot. Gość bez konta widzi swoją kwotę na telefonie płacącego i od razu robi przelew.

**Problem.** Przy wspólnych zakupach (mieszkanie, wyjazd, impreza) ręczne liczenie z paragonu jest żmudne i podatne na błędy, a ustalenia giną w wiadomościach.

**Główny przepływ:**

1. Zdjęcie paragonu aparatem.
2. Automatyczny odczyt tekstu (OCR) i dashboard z danymi do poprawienia.
3. Wybór uczestników (konta z wyszukiwarki lub goście) i osoby, która zapłaciła.
4. Przypisanie produktów do osób checkboxami, domyślnie wszystko wspólne.
5. Podsumowanie: ile każdy jest winny płacącemu.
6. Wysłanie powiadomień osobom z kontem i zapis rozliczenia w historii na telefonie.

**Przykład.** Płacę za cały paragon 22,00 zł. Ania jest mi winna 10,00 zł: 5,00 zł za chipsy i połowę płynu do naczyń.

| Produkt | Cena (zł) | Przypisanie | Udział: Ja (zł) | Udział: Ania (zł) |
| --- | --- | --- | --- | --- |
| Chipsy | 5,00 | Ania | 0,00 | 5,00 |
| Ciastka | 7,00 | Ja | 7,00 | 0,00 |
| Płyn do naczyń | 10,00 | wspólne | 5,00 | 5,00 |
| **Razem** | **22,00** |  | **12,00** | **10,00** |

Mój udział (12,00 zł) nie tworzy długu, bo to ja płaciłem; do zwrotu jest tylko udział Ani.

## Wymagania funkcjonalne

MVP to 19 wymagań Must: konto, skan, odczyt, edycja, uczestnicy i goście, przypisanie, wyliczenie długów, push i historia. Should i Could to rozszerzenia na kolejne iteracje; priorytet można zmienić w kolumnie.

| ID | Obszar | Wymaganie | Priorytet |
| --- | --- | --- | --- |
| FR-01 | Skanowanie | Zdjęcie paragonu aparatem z wykrywaniem krawędzi, przycięciem i wyprostowaniem perspektywy | Must |
| FR-02 | Skanowanie | Powtórzenie zdjęcia przed analizą | Must |
| FR-03 | Skanowanie | Import zdjęcia paragonu z galerii | Should |
| FR-04 | Odczyt (OCR) | Rozpoznanie tekstu na telefonie, bez internetu i bez usług w chmurze | Must |
| FR-05 | Odczyt (OCR) | Wyodrębnienie pozycji: nazwa, ilość, cena jednostkowa, wartość | Must |
| FR-06 | Odczyt (OCR) | Wyodrębnienie sumy (SUMA PLN), nazwy sklepu i daty zakupu | Must |
| FR-07 | Odczyt (OCR) | Doliczenie rabatów („Rabat”, „Opust”) do pozycji, której dotyczą | Should |
| FR-08 | Odczyt (OCR) | Ostrzeżenie, gdy suma pozycji różni się od sumy z paragonu | Must |
| FR-09 | Edycja | Edycja każdego pola pozycji oraz sklepu, daty i sumy | Must |
| FR-10 | Edycja | Ręczne dodawanie i usuwanie pozycji | Must |
| FR-11 | Edycja | Podgląd zdjęcia z powiększeniem, do porównania z odczytem | Should |
| FR-12 | Edycja | Rozliczenie wpisane ręcznie, bez zdjęcia | Could |
| FR-13 | Uczestnicy | Wyszukanie użytkownika z kontem po nazwie lub e-mailu i dodanie go do rozliczenia | Must |
| FR-14 | Uczestnicy | Dodanie gościa bez konta po samym imieniu; gość nie dostaje powiadomienia, swoją kwotę widzi na telefonie płacącego | Must |
| FR-15 | Uczestnicy | Wskazanie osoby, która zapłaciła | Must |
| FR-16 | Uczestnicy | Lista ostatnio wybranych osób do szybkiego dodania | Could |
| FR-17 | Przypisanie | Checkbox osoby przy każdej pozycji; domyślnie zaznaczeni wszyscy (pozycja wspólna) | Must |
| FR-18 | Przypisanie | Rozbicie pozycji wielosztukowej na sztuki, każda z własnymi checkboxami (np. 3 piwa: 2 dla mnie, 1 dla Ani) | Should |
| FR-19 | Przypisanie | Bieżąca suma na osobę widoczna podczas przypisywania | Should |
| FR-20 | Przypisanie | Szybkie akcje: „wszyscy”, „tylko ja” | Could |
| FR-21 | Rozliczenie | Dług osoby = suma jej udziałów; pozycja dzielona po równo między zaznaczonych | Must |
| FR-22 | Rozliczenie | Zaokrąglenie do grosza: nadwyżkę dostają kolejne osoby z listy po 1 gr, tak by suma udziałów = suma paragonu | Must |
| FR-23 | Rozliczenie | Rozbicie długu każdej osoby na pozycje | Should |
| FR-24 | Powiadomienia | Push do uczestników z kontem: kto prosi, kwota, za jaki paragon | Must |
| FR-25 | Powiadomienia | Lista otrzymanych próśb (tylko podgląd): od kogo, kwota, pozycje; tu otwiera się kliknięte powiadomienie | Should |
| FR-26 | Historia | Zapis w lokalnej bazie: zdjęcie, pozycje, przypisania, płacący, kwoty | Must |
| FR-27 | Historia | Lista zapisanych paragonów od najnowszych: sklep, data, suma | Must |
| FR-28 | Historia | Szczegóły paragonu z możliwością poprawki i ponownego przeliczenia | Should |
| FR-29 | Historia | Usunięcie paragonu z historii | Must |
| FR-30 | Konta | Rejestracja i logowanie e-mailem i hasłem | Must |
| FR-31 | Konta | Wylogowanie i usunięcie konta | Should |

## Wymagania niefunkcjonalne

Najważniejsze są dwa: kwoty muszą się zgadzać co do grosza, a cały przepływ poza wysyłką powiadomień musi działać offline. Wartości liczbowe to cele do potwierdzenia testami na prawdziwych paragonach.

| ID | Kategoria | Wymaganie |
| --- | --- | --- |
| NFR-01 | Platforma | Android 8.0+ (API 26+), telefony z ekranem 5–7″, orientacja pionowa |
| NFR-02 | Poprawność kwot | Kwoty przechowywane i liczone w groszach jako liczby całkowite (bez float/double); suma udziałów zawsze równa sumie paragonu |
| NFR-03 | Offline | Skan, OCR, edycja, podział i historia działają bez internetu; prośby czekają w kolejce i wychodzą po odzyskaniu sieci |
| NFR-04 | Wydajność | Odczyt paragonu do 30 pozycji ≤ 3 s na telefonie średniej klasy; zimny start ≤ 2 s; odpowiedź API ≤ 500 ms |
| NFR-05 | Skuteczność OCR | ≥ 80% pozycji odczytanych bez poprawek na zestawie 20 paragonów z Biedronki |
| NFR-06 | Trwałość danych | Historia przetrwa zamknięcie aplikacji i restart telefonu; niedokończone rozliczenie zapisuje się jako szkic |
| NFR-07 | Prywatność | Zdjęcia paragonów i dane gości nie opuszczają telefonu; na serwer trafia tylko prośba o zwrot (kwota, pozycje, sklep, data) |
| NFR-08 | Bezpieczeństwo | Komunikacja wyłącznie przez HTTPS; hasła hashowane bcrypt; API autoryzowane tokenem JWT; prośbę widzi tylko nadawca i odbiorca; klucz FCM tylko na serwerze |
| NFR-09 | RODO | Usunięcie konta usuwa dane użytkownika z serwera; lokalną historię można wyczyścić jednym przyciskiem |
| NFR-10 | Pamięć | Zdjęcie zapisywane jako JPEG o dłuższym boku ≤ 2000 px (ok. 0,3–0,6 MB na paragon) |
| NFR-11 | Wdrożenie | Backend z bazą startuje na VPS  z dockerem; adres API ustawiany w konfiguracji builda aplikacji |

## Widoki i przepływ

Rdzeń aplikacji to pięć ekranów w jednej ścieżce: aparat → dashboard → uczestnicy → przypisanie → podsumowanie. Pozostałe widoki (historia, otrzymane prośby, ustawienia) obsługują to, co dzieje się przed i po rozliczeniu.

&#91;embedded content: mapa ekranów · 9 widoków, ścieżka główna 5 kroków\]

Linia przerywana to powiadomienie push, które trafia na telefon dłużnika. Ustawienia otwiera się z menu ekranu głównego. Gość nie dostaje powiadomienia: swoją kwotę widzi na telefonie płacącego.

| # | Widok | Co pokazuje | Główne akcje |
| --- | --- | --- | --- |
| 1 | Logowanie | Rejestracja i logowanie e-mailem i hasłem; przy rejestracji imię widoczne dla innych | Zaloguj, załóż konto |
| 2 | Ekran główny (historia) | Lista paragonów od najnowszych: sklep, data, suma, kto płacił | Skanuj (duży przycisk), otwórz paragon |
| 3 | **Aparat** | Podgląd z kamery z ramką wykrytego paragonu, lampa, import z galerii; po zdjęciu podgląd przyciętego skanu | Zrób zdjęcie, powtórz, użyj |
| 4 | **Dashboard paragonu** | Sklep, data, suma; pozycje (nazwa, ilość, cena, wartość) z edycją w miejscu; baner, gdy suma się nie zgadza; miniatura zdjęcia | Edytuj, dodaj lub usuń pozycję, Dalej |
| 5 | **Uczestnicy i płacący** | Wyszukiwarka użytkowników z kontem (nazwa lub e-mail), ostatnio wybrane osoby, pole „dodaj gościa”; wybór, kto zapłacił | Wyszukaj, dodaj gościa, wskaż płacącego, Dalej |
| 6 | **Przypisanie produktów** | Każda pozycja z rzędem checkboxów (inicjały osób), domyślnie wszystkie zaznaczone; przy ilości > 1 przycisk „rozbij na sztuki”; pasek z sumą na osobę | Zaznacz lub odznacz osoby, rozbij na sztuki, Przelicz |
| 7 | **Podsumowanie** | Kwota do oddania dla każdej osoby z rozbiciem na pozycje; goście oznaczeni jako „bez powiadomienia”; udział płacącego; kontrola sumy | Wyślij powiadomienia, zapisz |
| 8 | Szczegóły paragonu | Zdjęcie, pozycje, przypisania, kwota każdej osoby | Edytuj i przelicz, usuń |
| 9 | Otrzymane prośby | Prośby od innych: od kogo, kwota, pozycje; tu prowadzi kliknięte powiadomienie | Podgląd szczegółów |
| 10 | Ustawienia | Profil, czyszczenie historii, usunięcie konta, wylogowanie | — |

## Technologia

**Kotlin + Jetpack Compose w Android Studio, backend Ktor + PostgreSQL na prywatnym VPS.** Rdzeń aplikacji to aparat i OCR, a oba mają oficjalne, darmowe biblioteki Google (ML Kit) działające offline, które w natywnym Androidzie podłącza się bez żadnych warstw pośrednich.

**S****t****a****c****k****:**

| Warstwa | Technologia |
| --- | --- |
| Język i UI | Kotlin, Jetpack Compose + Material 3&#32; |
| IDE | Android Studio Rabbit 1, 2026.2.1 (aplikacja, wersja ze slajdu); IntelliJ IDEA (backend) |
| Skanowanie | ML Kit Document Scanner;&#32; |
| OCR | ML Kit Text Recognition v2 (alfabet łaciński, na telefonie) |
| Parser paragonu | Własny moduł Kotlin: grupowanie linii po współrzędnych + wyrażenia regularne na wzorzec „ilość × cena wartość stawka PTU” |
| Lokalna baza | Room (SQLite); zdjęcia jako pliki w pamięci wewnętrznej aplikacji |
| Architektura aplikacji | MVVM, Coroutines + Flow, Navigation Compose |
| Komunikacja z API | Retrofit + kotlinx.serialization; token JWT w DataStore |
| Backend | Ktor (Kotlin), REST API, logowanie e-mail + hasło (bcrypt), JWT |
| Baza na serwerze | PostgreSQL + Exposed |

**Backend: Ktor na prywatnym VPS.** Serwer ma tylko trzy zadania: konta, wyszukiwarka użytkowników i prośby o zwrot z wysyłką push. Ktor jest w tym samym języku co aplikacja, więc modele danych można współdzielić.

**Push przez FCM.** Na Androidzie powiadomienia push w praktyce dostarcza Firebase Cloud Messaging. Używamy go wyłącznie jako darmowego kanału: bez bazy, funkcji i planu Blaze, cała logika zostaje na VPS.&#32;

## Architektura w skrócie

Zdjęcia i cała historia zostają na telefonie; serwer na VPS trzyma tylko konta i prośby o zwrot, a jego głównym zadaniem jest dostarczenie powiadomienia.

&#91;embedded content: architektura · 2 telefony, VPS, FCM\]

Wysyłka prośby krok po kroku:

1. Aplikacja płacącego wysyła do API jedną prośbę na każdego uczestnika z kontem (gości pomija).
2. API zapisuje prośbę w PostgreSQL i wysyła push przez FCM na token telefonu odbiorcy.
3. Kliknięcie powiadomienia otwiera widok „Otrzymane prośby”, który pobiera szczegóły z API.

Bez internetu prośby czekają w kolejce WorkManagera i wychodzą po odzyskaniu sieci. API wystarczy siedem endpointów:

| Endpoint | Do czego |
| --- | --- |
| POST /auth/register, POST /auth/login | Rejestracja i logowanie, zwraca token JWT |
| GET /users?q= | Wyszukiwanie użytkowników po nazwie lub e-mailu |
| PUT /me/fcm-token | Zapis tokenu FCM telefonu po zalogowaniu |
| DELETE /me | Usunięcie konta i jego próśb |
| POST /requests | Utworzenie prośby o zwrot i wysłanie push |
| GET /requests/incoming | Lista otrzymanych próśb |
