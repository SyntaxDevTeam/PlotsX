# 06 — Postęp implementacji

[Spis planu](README.md) · [Etapy i kryteria odbioru](05-realizacja-testy-wdrozenie.md) · [API i uruchomienie](07-api-v2-i-uruchomienie.md) · [Raport testów](08-raport-weryfikacji.md)

Aktualizacja: **19 września 2026 r.** `[x]` oznacza wykonany zakres, `[ ]` pracę pozostałą. **Podłączono zajmowanie i zakup chunków, trwały dziennik płatności, kompensację oraz GUI.** Tryb `classic | chunks` wybiera sposób tworzenia nowych działek, a zapisany typ działki wybiera późniejszy sposób jej rozszerzania. Nie oznacza to zakończenia całego wydania: akceptacja z rzeczywistymi dostawcami, pełne interfejsy i staging pozostają w E5–E8.

## Iteracja 1 — Fundament geometrii i kontrakt wyboru trybu

Stan: **zakończona**.

- [x] Przeanalizowano modele, inicjalizację, konfigurację i dotychczasowe testy.
- [x] Wspólny kontrakt geometrii i domkniętych granic `BlockBounds`, obliczenia w `Long`.
- [x] Adapter `ClassicGeometry` zachowujący istniejące segmenty i rozszerzanie.
- [x] `ChunkGeometry`: współrzędne ujemne, spójność, sąsiedztwo, dokładna powierzchnia i kolizje mieszane.
- [x] Niemodyfikowalne kolekcje geometrii, eliminacja duplikatów i kontrola zakresu bloków.
- [x] `ClaimMode` oraz `ClaimGeometryFactory`: wybór `classic | chunks`, brak trybu oznacza `classic`.
- [x] Testy geometrii, parsera i regresja wcześniejszego zachowania.

Pierwsza iteracja zakończyła się wynikiem **52 testów bez błędów**. Był to fundament bez aktywnego zajmowania chunków; integrację runtime i konfiguracji wykonano poniżej.

## Iteracja 2 — Persystencja i migracje (E3)

Stan: **zakończona, wraz z wcześniej brakującą macierzą MySQL/PostgreSQL i integracją serwera**.

- [x] Oddzielono zamrożony schemat v1 od schematu chunkowego.
- [x] Dodano `geometry_type`, `geometry_revision`, nullable `radius`, `plot_chunks` i `schema_migrations`.
- [x] Unikalność chunków świata, klucz obcy z kaskadą i indeks po ID działki.
- [x] Kanoniczny klucz świata i binarna kolacja MySQL/MariaDB, bez utożsamiania nazw różniących się znakami diakrytycznymi.
- [x] Migracja zachowuje ID, dane i powiązania klasycznych działek.
- [x] SQLite: przebudowa transakcyjna, stan FK, licznik ID, rollback po błędzie.
- [x] H2/MySQL/MariaDB/PostgreSQL: powtarzalne kroki DDL i znacznik zakończenia zapisywany na końcu.
- [x] Repozytorium zapisuje metadane i rzeczywistą geometrię obu typów w transakcji wywołującego.
- [x] Savepoint zapobiega pozostawieniu części działki po konflikcie wewnątrz paczki.
- [x] Zbiorczy odczyt zachowuje typ i rewizję; odrzuca nieznany typ, puste/rozłączne chunki, błędny świat i osierocone rekordy.
- [x] Backup v2 obsługuje chunki; v1 i starsze backupy bez segmentów pozostają obsługiwane.
- [x] Import waliduje geometrię i kolizje mieszane przed commit; błędny import wycofuje dane.
- [x] Testy migracji, zapisu, rollbacku, kasowania oraz backupów na SQLite i H2.
- [x] Te same scenariusze na **rzeczywistych MariaDB 11.8.6, MySQL 8.4.11 i PostgreSQL 18.6**.
- [x] Pełna macierz **25 kombinacji źródło–cel** backupu przy włączonych pięciu silnikach.
- [x] `DatabaseHandler.createTables()` automatycznie migruje bazę przed uruchomieniem cache i ochrony.
- [x] `PlotRepository` podłączono do runtime, cache, resolvera, API i koordynowanych mutacji.
- [x] Import przez plugin dopuszcza poprawne chunki i publikuje wynik pod barierą ochrony. Wcześniejsza tymczasowa blokada została usunięta.

### Kontrakty i ograniczenia

`DatabaseSchema.statements()` nadal opisuje v1; jego niezmienność jest kontraktem starszych backupów. `DatabaseMigrations.migrate()` działa na dedykowanym połączeniu przed obsługą zdarzeń lub pod barierą importu. Nieznane wersje schematu są odrzucane.

DDL na części silników nie jest wycofywalne razem z importowanymi danymi. Nieudany import odtwarza poprzednią zawartość, lecz schemat może pozostać zaktualizowany. Repozytorium nie zastępuje autoryzacji i limitów; zapewnia je wyższa warstwa. Walidacja wszystkich kolizji przy imporcie ma koszt kwadratowy i nie jest używana w zwykłych decyzjach ochrony.

Testy zewnętrzne używają losowych baz/schematów `plotsx_test_*` w osobnych lokalnych instancjach. Dotychczasowy wynik historyczny wynosił 67 testów; końcowe wyniki integracji są w raporcie 08. Dziennik płatności `plot_operations` pozostaje elementem E6, a nie ukończonej migracji geometrii.

## Iteracja 3 — Indeks, spójny runtime i ochrona (E4)

Stan: **zakończona; uzupełniono część B i weryfikację działającego serwera**.

- [x] `SpatialPlotIndex<T>` indeksuje obie geometrie, z kanonicznym kluczem świata.
- [x] Lokalny odczyt chunków i dokładne sprawdzanie klasycznych kandydatów zachowują wolne dziury/narożniki.
- [x] Limit 1024 kubełków dla klasycznej działki; duże obszary trafiają do dokładnego fallbacku danego świata.
- [x] `SnapshotStore`: atomowa publikacja, powtarzanie spóźnionego odczytu i epoki unieważniające odczyty sprzed czyszczenia.
- [x] `PlotCacheSnapshot` publikuje razem działki, indeks, flagi i członków; kolekcje są odłączone i niemodyfikowalne.
- [x] Odświeżenie jednej działki ładuje razem geometrię i metadane; aktualizacja samych metadanych zachowuje indeks.
- [x] Runtime `PlotData` reprezentuje oba typy: chunk ma `radius = null`, jawny zbiór chunków i rewizję.
- [x] `PlotCacheLoader` zastąpił `ClassicCacheLoader`; ładuje obie geometrie z repozytorium w spójnej transakcji SQL i propaguje błędy.
- [x] Komendy, podstawowe GUI, lista działek, wizualizacja i bezpieczny teleport uwzględniają brak klasycznego promienia.
- [x] Listener ochrony i API korzystają ze wspólnego resolvera; decyzje nie wykonują zapytań SQL.
- [x] API v2: typ, nullable promień, chunki, dokładna powierzchnia, rewizja i `contains()`.
- [x] Testy konsumentów Java/Kotlin oraz test rzeczywistej niezgodności binarnej v1; instrukcja przebudowy integracji w dokumencie 07.
- [x] Wszystkie wieloetapowe decyzje pojedynczego handlera ochrony korzystają z przypiętego snapshotu.
- [x] Wspólny `ProtectionCoordinator` obejmuje mutacje geometrii, flag, członków, własności, usunięcia i importu aż do publikacji cache.
- [x] Ochrona odmawia działań w luce między zapisem a publikacją i po awarii publikacji; udany reload umożliwia odzyskanie działania.
- [x] Testy wymuszonej kolejności odświeżeń, współbieżnych mutacji, rollbacku i odzyskiwania.
- [x] Test na Paper: oba zapisane typy, właściciel/gość, granice ujemne, wolny narożnik, tłok, płyny, API, zapis i usunięcie.
- [x] Test rzeczywistych zdarzeń i API w kontrolowanym oknie publikacji.
- [x] Test restartu z zachowaną mieszaną bazą i wyboru strategii; reload odrzuca podmianę aktywnego trybu.
- [x] Pomiary p50/p95/p99 i przybliżonej pamięci dla 1 tys., 10 tys. i 100 tys. chunków oraz klasycznych działek, w tym dużej geometrii.

### Przyjęte rozwiązanie spójności

Rezerwacja jest globalną barierą **jednego serwera**, a nie mapą blokad pojedynczych regionów. Podczas mutacji anulowalne zdarzenia ochrony są chwilowo blokowane również poza zmienianą działką. `evaluateFlag()` odmawia, a zwykłe odczyty snapshotów API mogą nadal zwracać poprzednią opublikowaną wersję. Wątek zdarzenia nie czeka na bazę. Błąd publikacji pozostawia blokadę do poprawnej odbudowy cache.

Snapshot jest przypięty na czas jednego handlera. Osobne handlery/priorities mogą zobaczyć kolejną wersję, jednak każda wieloetapowa decyzja pozostaje spójna i chroniona tą samą barierą. Pełny loader v2 wykonuje sześć zbiorczych zapytań o dane, poza sprawdzeniem metadanych schematu. Odczyt pojedynczej działki nadal odczytuje zbiorczo geometrię przed filtrowaniem ID; optymalizacja tej ścieżki nie jest warunkiem poprawności.

Benchmark jest pomiarem lokalnym na syntetycznych zbiorach przewidzianych w planie. Budżet rzeczywistego serwera, porównanie TPS, pełne pomiary operacji płatnych i staging pozostają E8. Fallback dużych klasycznych działek ma koszt liniowy względem liczby takich działek w świecie.

## Iteracja 4 — Następny etap: zajmowanie (E5)

Stan: **implementacja podłączona, testy transakcji i strategii serwera wykonane; akceptacja interfejsów gracza pozostaje otwarta**.

- [x] `config.yml`: aktywne `plots.claiming.mode` i limity chunków; domyślnie `classic`.
- [x] Walidacja trybu/limitów, odrzucenie zmiany trybu przez reload i wymagany restart.
- [x] Limity liczbowe z uprawnień w `HookHandler`.
- [x] `/claim` przygotowuje geometrię wybranej strategii; chunk zawsze ma 256 kolumn, bez zależności od promienia.
- [x] Potwierdzenie kontroluje świat i geometrię oferty, ponownie sprawdza uprawnienia i regiony.
- [x] WorldGuard otrzymuje dokładne prostokątne granice, również parzysty bok 16.
- [x] `ClaimTransaction`: wspólne limity liczby działek/powierzchni, limity chunków, kolizje mieszane, metadane, geometria, flagi i log w jednej transakcji.
- [x] Zapis i synchroniczna publikacja cache są koordynowane; sukces jest zwracany po publikacji.
- [x] Test dwóch konkurujących zajęć: jeden sukces przy kolizji mieszanej i jeden sukces przy wspólnym limicie właściciela.
- [x] Test awarii zapisu flag nie pozostawia metadanych ani chunków.
- [x] Transfer liczy prawdziwą powierzchnię i limity chunków odbiorcy; usunięcie działa z kaskadą i natychmiastową publikacją.
- [x] Podstawowy opis oferty chunkowej w dialogu/GUI oraz komunikaty PL/EN.
- [ ] Akceptacja pełnej sekwencji `/claim` z rzeczywistym klientem w obu interfejsach, podwójnego kliknięcia i ruchu przy otwartym potwierdzeniu.
- [ ] Test integracyjny WorldGuard z zainstalowanym pluginem, regionem na części chunka i awarią odczytu.

## Iteracja 5 — E6, część A: transakcja rozszerzania chunków

Stan: **warstwa transakcyjna zaimplementowana i przetestowana; E6 jako całość pozostaje otwarty**. Pełny zestaw lokalny: **107 testów, bez błędów**. Szczegóły kontraktu i dalszej integracji: [09 — Rozszerzanie chunków](09-rozszerzanie-chunkow.md).

- [x] Żądanie rozszerzenia wskazuje działkę, właściciela, aktora, źródłowy chunk, kierunek, oczekiwany cel i rewizję oferty.
- [x] Ponowny odczyt SQL odrzuca usuniętą działkę, nieaktualnego właściciela, klasyczny typ i nieaktualną rewizję.
- [x] Dodawany jest dokładnie jeden wolny chunk przylegający bokiem do wskazanego chunka tej działki.
- [x] Walidacja wspólnej powierzchni właściciela we wszystkich światach oraz limitów chunków działki i właściciela.
- [x] Dokładne kolizje z obiema geometriami, także z drugą działką tego samego właściciela.
- [x] Wspólny commit chunka, rewizji, `plot_expansion_levels` i historii z aktorem operacji.
- [x] Starsze importy chunkowe bez poziomu rozszerzeń otrzymują poziom wynikający z istniejącej liczby chunków; istniejący poprawny licznik jest zachowywany.
- [x] Wewnętrzna ścieżka `DatabaseHandler.expandChunkAtomically()` obejmuje zapis i publikację wspólnym koordynatorem.
- [x] Oddzielny wariant pracujący w transakcji wywołującego umożliwia przyszły wspólny commit z dziennikiem płatności.
- [x] Klasyczna ścieżka rozszerzania odrzuca chunk przed próbą interpretacji NULL promienia jako zera.
- [x] Testy granic, limitów, kolizji, błędów zapisu, rollbacku zewnętrznego i inicjalizacji licznika na SQLite/H2.
- [x] Wymuszona konkurencja dwóch ofert tej samej rewizji: jeden sukces, jedna nieaktualna oferta, jeden przyrost powierzchni/poziomu i zgodny cache.
- [x] Dziennik `plot_operations`, migracja i backup v3 — wykonane w iteracji 6 poniżej.
- [x] Osobny cennik, zachowany dostawca, obciążenia i zwroty — podłączone w iteracji 7.
- [ ] Wewnętrzna obsługa operacji niepewnych bez wystawiania technicznych komend w głównym interfejsie gracza.
- [x] Podłączenie zakupu w GUI ekwipunkowym z ponowną kontrolą uprawnień, położenia gracza, WorldGuard i ceny — iteracja 7.
- [ ] Natywne dialogi oraz akceptacja UI z klientem.
- [ ] Testy nowego rozszerzania na pozostałych silnikach i działającym Paper po integracji usługi zakupu.

Na koniec iteracji 5 nie wystawiono tej metody jako zakupu ani nie usunięto blokady chunkowego GUI. Iteracja 7 poniżej podłącza usługę zakupu. Warstwa zapisu nie pobiera pieniędzy i nie zastępuje autoryzacji ani zewnętrznych regionów; udostępnienie płatnego przycisku przed dziennikiem przeczyłoby kontraktowi E6.

## Iteracja 6 — E6 B: trwały dziennik i odzyskiwanie stanów

Stan na koniec iteracji 6: **model i persystencja dziennika wykonane**. Podłączenie dostawcy i GUI opisuje iteracja 7. Szczegóły: [10 — Dziennik operacji](10-dziennik-operacji.md).

- [x] `plot_operations`: tożsamość oferty, aktor/właściciel, dokładna kwota, dostawca/waluta, stan i znaczniki czasu.
- [x] Migracja `2 / operation_journal`, uruchamiana przy starcie po migracji geometrii; kontrola utraconej tabeli dziennika.
- [x] Jawne stany i warunkowe przejścia; ponowione ID nie pozwala przygotować kolejnego obciążenia.
- [x] Przygotowanie nowej operacji odrzuca nierozliczoną operację właściciela lub działki.
- [x] Wspólny commit rozszerzenia i `LAND_COMMITTED`; rollback obejmuje obie strony zapisu JDBC.
- [x] Recovery po restarcie klasyfikuje przerwane próby bez wywoływania dostawcy i raportuje nierozliczone wpisy.
- [x] Historia finansowa przetrwa usunięcie działki.
- [x] Backup v3 zachowuje dziennik; import waliduje dane i kieruje nierozliczone wpisy do `UNCERTAIN`.
- [x] Import nie nadpisuje bieżących nierozliczonych płatności; zachowana obsługa v1/v2.
- [x] 15 nowych testów na SQLite/H2; pełny lokalny zestaw **122 testów bez błędów**; `shadowJar` zbudowany.
- [x] Usługa zakupu, adaptery ekonomii, cennik i rezerwacja całego przebiegu — kod podłączony w iteracji 7.
- [ ] Weryfikacja z rzeczywistym dostawcą ekonomii.
- [x] Podłączenie GUI ekwipunkowego — iteracja 7.
- [ ] Natywne dialogi i testy awarii z rzeczywistym dostawcą.
- [ ] Weryfikacja nowej migracji i backupu v3 na MySQL/MariaDB/PostgreSQL oraz integracja na Paper.

## Iteracja 7 — E6 C: zakup w tle i GUI chunków

Stan: **usługa zakupu i GUI podłączone; testy kontrolowanych awarii przechodzą**. [Kontrakt, cennik i wyniki](11-zakup-chunka-i-gui.md).

- [x] `ChunkPurchaseService` łączy walidację, dziennik, obciążenie, zapis, ewentualny zwrot i publikację.
- [x] JDBC pracuje w tle; Bukkit i dostawca ekonomii na wątku serwera.
- [x] Rezerwacja między wątkami nie trzyma blokady JVM podczas oczekiwania na callback.
- [x] Po niejednoznacznym commit następuje kontrola dziennika na nowym połączeniu przed decyzją o zwrocie.
- [x] Wyjątki płatności/zwrotu i awaria publikacji nie powodują automatycznego ponownego obciążenia.
- [x] Dostawca pozostaje zachowany; zakup chunkowy kontroluje walutę i przypina świat/walutę VaultUnlocked.
- [x] Osobny cennik `plots.chunks.expansion.price/priceMultiplier`, cena 0 bez dostawcy.
- [x] `PlotCacheLoader` ładuje poziom cenowy; wskazany test rozszerzono o poziom zapisany w SQL i błędny licznik.
- [x] `ChunkExpandGUI`: kierunek, cel, cena, limit, granice, jednorazowe potwierdzenie oraz ponowna walidacja.
- [x] Usunięto blokadę otwierania chunkowego rozszerzania; aktywny tryb zajmowania nie zmienia typu istniejącej działki.
- [x] Komunikaty PL/EN dla przetwarzania, limitów, kolizji, zajętej rezerwacji i operacji wymagającej wyjaśnienia.
- [x] Klasyczne GUI nie obciąża konta przed odmową rezerwacji; reload odrzuca próbę przed podmianą configu.
- [x] 12 testów zakupu i 2 testy loadera; pełny zestaw **136 testów bez błędów**, `shadowJar` zbudowany.
- [ ] Akceptacja zakupu na Paper z prawdziwym dostawcą i klientem oraz natywne dialogi.
- [ ] Pozostałe silniki, platformy, testy obciążenia i staging.

## Etapy całościowe i dalsze prace

| Etap | Stan | Pozostało |
| --- | --- | --- |
| E1 — Kontrakty | Wykonano | Wersjonowanie v2 i zasada restartu są jawne. |
| E2 — Geometria | Wykonano | Rozwój geometrii rozszerzania w E6. |
| E3 — Persystencja | Wykonano | Następna migracja dziennika płatności należy do E6. |
| E4 — Ochrona | Wykonano w opisanym modelu pojedynczego serwera | Optymalizacje bariery i budżety produkcyjne w E8. |
| E5 — Zajmowanie | Kod i transakcje podłączone; akceptacja częściowo otwarta | Testy klienta/dialogów i WorldGuard wymienione powyżej. |
| E6 — Cykl życia | Zakup chunków z dziennikiem, ekonomią, kompensacją i GUI podłączony | Akceptacja zakupu z rzeczywistymi dostawcami/platformami i wewnętrzna obsługa stanów niepewnych. |
| E7 — Interfejsy | Podstawowe opisy, granice i komunikaty PL/EN | Pełna akceptacja GUI/dialogów, pozostałe języki, dokładne operacje CoreProtect. |
| E8 — Wydanie | Macierz SQL, pierwszy test Paper i benchmark wykonane | Pozostałe wersje/platformy, staging, testy obciążenia oraz próba wycofania całego wydania. |

**Kolejny zakres E6:** test zakupu na Paper z rzeczywistym dostawcą i klientem. GUI ekwipunkowe rozszerzania chunków jest już podłączone; techniczne komendy płatności nie są częścią interfejsu produktu. Natywne dialogi i pełna akceptacja E5/E7/E8 pozostają otwarte. Wyniki testów kontrolowanych dostawców nie są deklaracją gotowości całego wydania.
