# 06 — Postęp implementacji

[Spis planu](README.md) · [Etapy i kryteria odbioru](05-realizacja-testy-wdrozenie.md)

Status aktualizowany wraz ze zmianami w kodzie. `[x]` oznacza wykonany zakres, `[ ]` pracę pozostałą. Ukończenie części etapu nie oznacza gotowości całej funkcji.

## Iteracja 1 — Fundament geometrii i kontrakt wyboru trybu

Stan: **implementacja fundamentu zakończona; testy przechodzą**. Chunkowe zajmowanie nie jest jeszcze dostępne przez komendy. Pierwsza iteracja wydziela geometrię klasyczną i dodaje testowalny model chunków przed zmianami SQL i ochrony.

- [x] Przeanalizowano aktualne modele, inicjalizację, konfigurację i testy geometrii.
- [x] Wspólny kontrakt geometrii i dokładnych granic prostokąta.
- [x] Przeniesienie klasycznej geometrii do adaptera z zachowaniem zachowania `PlotData`.
- [x] Model chunków: współrzędne ujemne, powierzchnia, spójność, sąsiedztwo i kolizje mieszane.
- [x] Kontrakt wyboru `classic | chunks` i przygotowania geometrii nowego zajęcia.
- [x] Testy nowych kontraktów oraz regresja istniejących testów.
- [x] Zapis wyników weryfikacji i aktualizacja listy dalszych prac.

### Wykonane zmiany w kodzie

Ścieżki głównego kodu są względne wobec `src/main/kotlin/pl/syntaxdevteam/plotsx/`.

| Plik | Wykonany zakres |
| --- | --- |
| `geometry/PlotGeometry.kt` | Wspólny kontrakt powierzchni, przynależności, regionów i przecięć; domknięte granice `BlockBounds` liczone w `Long`. |
| `geometry/ClassicGeometry.kt` | Adapter istniejących segmentów, zachowanie sumowania powierzchni i rozszerzania z wybranego źródła. |
| `databases/PlotData.kt` | Delegowanie dotychczasowych metod do geometrii klasycznej; zachowane argumenty konstruktora i metody wywoływane przez obecny kod. |
| `geometry/ChunkGeometry.kt` | `ChunkPosition`, dzielenie z zaokrągleniem w dół, niepusta spójna geometria, eliminacja duplikatów, odporne na mutacje kolekcje, sąsiedztwo i granice zakresu bloków. |
| `claiming/ClaimMode.kt` | Parser zawartości sekcji `plots.claiming`: brak `mode` oznacza `classic`; jawne NULL, zły typ i nieznana wartość są odrzucane. |
| `claiming/ClaimGeometryFactory.kt` | Przygotowanie klasycznego segmentu lub dokładnie jednego chunka; promień nie wpływa na chunk; kontrola zakresu klasycznego kandydata. |

Parser przyjmuje mapę wartości sekcji, a fabryka jedynie tworzy geometrię w pamięci. **Nie podłączono ich jeszcze do Bukkit configu ani `ClaimCMD` i nie dodano aktywnego przełącznika do `config.yml`.** Na zakończenie iteracji 1 nie było jeszcze persystencji chunków; jej implementację opisuje iteracja 2 poniżej. `PlotData` nadal reprezentuje wyłącznie dane klasyczne. Sprawdzenia geometrii nie zastępują kontroli świata, własności, limitów lub regionów zewnętrznych.

### Weryfikacja iteracji

- `./gradlew test --offline --console=plain` — kompilacja kodu głównego i testów oraz testy zakończone powodzeniem.
- Wynik raportów JUnit: **52 testy, 0 błędów, 0 niepowodzeń, 0 pominiętych**; w tej iteracji dodano 24 testy. Łączny wynik obejmuje również testy obecne wcześniej w katalogu roboczym.
- Dodano testy `ChunkGeometryTest`, `ClassicGeometryTest` i `ClaimModeTest`.
- Sprawdzono dokładną powierzchnię 256, współrzędne ujemne, wklęsłe kształty i dziury, kolizję pojedynczą kolumną, obie kolejności kolizji mieszanej, granice `Int`, izolację kolekcji i niepoprawny tryb.
- Dotychczasowe `PlotGeometryTest` nadal przechodzą, w tym rozszerzanie klasyczne i snapshoty API.
- W iteracji 1 nie wykonywano testów działającego serwera, migracji chunków ani płatności.

## Iteracja 2 — Persystencja i migracje (E3)

Stan: **warstwa persystencji zaimplementowana i sprawdzona na SQLite, H2 oraz MariaDB 11.8.6; E3 pozostaje częściowo otwarty**. Brakuje weryfikacji na rzeczywistym MySQL i PostgreSQL oraz podłączenia nowego repozytorium do wspólnej ochrony w E4. Bieżący serwer nadal obsługuje wyłącznie klasyczne działki.

- [x] Oddzielono zamrożony schemat v1 od nowego schematu chunkowego.
- [x] Dodano `geometry_type`, `geometry_revision`, nullable `radius`, `plot_chunks` i `schema_migrations`.
- [x] Dodano unikalność chunków świata, klucz obcy z kaskadowym usunięciem i indeks po ID działki.
- [x] Klucz świata używa binarnej kolacji w MySQL/MariaDB, aby np. `world` i `wórld` nie kolidowały przez ustawienia serwera SQL.
- [x] Zaimplementowano jawną migrację z zachowaniem identyfikatorów, danych i powiązań działek klasycznych.
- [x] SQLite: przebudowa tabeli w transakcji, zachowanie licznika ID, przywracanie ustawień FK i rollback po błędzie.
- [x] H2/MySQL/MariaDB/PostgreSQL: powtarzalne kroki DDL, wykrywanie istniejących kolumn i zapis znacznika ukończenia na końcu.
- [x] Repozytorium zapisuje metadane i rzeczywistą geometrię, także wszystkie rozszerzenia klasyczne i pierwszy chunk.
- [x] Zapis korzysta z transakcji wywołującego i savepointu, aby konflikt w środku paczki nie pozostawiał części działki.
- [x] Zbiorczy odczyt zachowuje zapisany typ i rewizję; odrzuca nieznany typ, pustą/rozłączną geometrię, błędny świat i osierocone rekordy.
- [x] Format backupu v2 obsługuje chunki; zachowano eksport i import v1, również starsze backupy bez segmentów.
- [x] Import weryfikuje geometrię i kolizje mieszane przed commit; nieudany import wycofuje dane.
- [x] Import przez `DatabaseHandler` odrzuca chunkowe działki do czasu gotowości E4.
- [x] Testy migracji, zapisu, rollbacku, kasowania i backupów SQLite ↔ H2 ↔ MariaDB.
- [ ] Testy tych samych scenariuszy na rzeczywistych MySQL i PostgreSQL.
- [ ] Automatyczne uruchamianie migracji podczas startu po wdrożeniu ochrony obu typów.
- [ ] Podłączenie repozytorium do cache, resolvera, API oraz wspólnego koordynatora mutacji.

### Zmiany i kontrakty persystencji

| Plik | Wykonany zakres |
| --- | --- |
| `databases/DatabaseSchema.kt` | `statements()` zachowuje stary schemat; `chunkStatements()` opisuje docelowy schemat z geometrią i wersjonowaniem. |
| `databases/DatabaseMigrations.kt` | `migrate()` wymaga dedykowanego połączenia auto-commit i pracy w trybie utrzymaniowym. Rozpoznaje stare i częściowo zmienione schematy, odrzuca nieznane wersje. |
| `databases/PlotGeometryRepository.kt` | Zbiorczy odczyt geometrii, zapis chunków z kanonicznym kluczem świata oraz walidacja kolizji podczas importu. |
| `databases/PlotRepository.kt` | `StoredPlot` z jawną geometrią, zapis obu typów i odczyt metadanych bez interpretowania chunka jako promienia. |
| `databases/SqlBackup.kt` | Rozpoznawanie schematu źródła, eksport v1/v2, import obu formatów, przywracanie stanu połączenia i walidacja danych v2. |
| `databases/DatabaseHandler.kt` | Jawna blokada importu chunków w obecnej ścieżce serwera. |
| `src/test/kotlin/.../databases/ChunkPersistenceTest.kt` | Scenariusze nowej persystencji na SQLite i H2 oraz opcjonalnej, odizolowanej instancji MariaDB. |
| `build.gradle.kts` | Sterownik MariaDB dostępny również podczas testów integracyjnych. |

Repozytorium jest warstwą zapisu, a nie serwisem zajmowania działki. Wywołujący musi zapewnić autoryzację, limity, koordynację kolizji oraz transakcję. Savepoint chroni przed częściowym zapisem, ale nie zastępuje wspólnego koordynatora mutacji. Zbiorczy odczyt wymaga niezmienianej równolegle bazy lub transakcji zapewniającej spójny snapshot. Walidator kolizji importu ma koszt kwadratowy w liczbie działek danego świata i nie nadaje się do wywoływania przy zdarzeniach ochrony.

**Aktywacja jest celowo etapowa:** nie zmieniono startowego `createTables()` na automatyczną migrację. Migrację uruchamia jawnie warstwa utrzymaniowa, w tym odtwarzanie backupu v2. Import chunków wymaga dodatkowo `allowChunkPlots = true`; domyślnie jest zabroniony, a ścieżka pluginu przekazuje `false`. Nie należy uruchamiać obecnego serwera na bazie zawierającej chunki zapisane przez narzędzia utrzymaniowe — resolver i API zostaną dostosowane w E4.

Odtwarzanie v2 może zaktualizować schemat przed transakcją importu. Odrzucenie danych przywraca poprzednią zawartość, ale nie obiecuje cofnięcia DDL na każdym silniku. Zachowanie starego schematu v1 jest również kontraktem formatu backupu; nie należy go modyfikować przy kolejnych migracjach.

Nie dodano jeszcze dziennika płatności `plot_operations`: należy do E6 i pojawi się z kolejną wersją migracji oraz formatu backupu. Obecny znacznik schematu obejmuje wyłącznie geometrię.

### Weryfikacja iteracji 2

- `./gradlew test --offline --console=plain` — kompilacja i pełny zestaw testów przechodzą.
- Dodano 15 testów `ChunkPersistenceTest`; większość wykonuje scenariusz na każdym dostępnym silniku. Z włączoną MariaDB backupy sprawdzają dziewięć kombinacji źródło–cel.
- Wynik po iteracji: **67 testów, 0 błędów, 0 niepowodzeń, 0 pominiętych**, również podczas uruchomienia z testową MariaDB.
- Przetestowano wznowienie migracji po dodaniu części kolumn, odrzucenie nieznanej wersji, rollback uszkodzonej migracji SQLite i zachowanie licznika wcześniej usuniętych ID.
- Sprawdzono kolizję drugiego chunka w paczce, rollback zewnętrznej transakcji, kaskadowe usuwanie, niepoprawne importy i stan połączenia po błędzie.
- Testy MariaDB wykonano na tymczasowej instancji **11.8.6**, uruchomionej wyłącznie na localhost z osobnym katalogiem danych. Zestaw testów tworzył i usuwał bazy o losowych nazwach `plotsx_test_*`; po testach potwierdzono ich usunięcie i zatrzymano tymczasowy serwer. Istniejące bazy nie były używane.
- **Nie wykonano testów MySQL ani PostgreSQL na ich instancjach.** Sprawdzenie wspólnej gałęzi SQL na MariaDB nie jest deklaracją weryfikacji MySQL. Nie uruchamiano testów serwera Minecraft ani płatności.

Powtórzenie rozszerzonej macierzy wymaga uprzednio uruchomionej, odizolowanej MariaDB przeznaczonej wyłącznie do testów. `PLOTSX_TEST_MARIADB_URL` wskazuje jej bazowy URL JDBC bez nazwy bazy; aktualny harness używa konta `root` bez hasła w tej tymczasowej instancji. Nie wskazywać serwera produkcyjnego. Przykład dla lokalnego portu testowego:

```bash
PLOTSX_TEST_MARIADB_URL=jdbc:mariadb://127.0.0.1:13376/ ./gradlew test --offline --console=plain --rerun-tasks
```

Bez zmiennej środowiskowej testy uruchamiają tylko SQLite i H2. `--rerun-tasks` wymusza wykonanie po zmianie zestawu dostępnych silników.

## Etapy całościowe

| Etap | Stan | Pozostało |
| --- | --- | --- |
| E1 — Kontrakty | Częściowo: dokumentacja, parser trybu i fabryka geometrii gotowe | Adapter konfiguracji Bukkit; decyzja i weryfikacja zgodności publicznego API. |
| E2 — Geometria | Wykonano fundament i testy jednostkowe | Integracja obu geometrii z persystencją, ochroną i interfejsami w kolejnych etapach. |
| E3 — Persystencja | Implementacja i testy SQLite/H2/MariaDB wykonane; etap częściowo otwarty | Weryfikacja MySQL/PostgreSQL, integracja startu i repozytorium razem z E4. |
| E4 — Ochrona | Nie rozpoczęto | Resolver obu geometrii, indeksy, publikacja, rewizje, API. |
| E5 — Zajmowanie | Przygotowanie: fabryka geometrii gotowa | Podłączenie configu i strategii do komend; limity i wspólna walidacja transakcyjna. |
| E6 — Cykl życia | Nie rozpoczęto | Rozszerzanie, transfer, usunięcie, ekonomia i odzyskiwanie. |
| E7 — Interfejsy | Nie rozpoczęto | GUI/dialogi, granice, tłumaczenia i hooki. |
| E8 — Wydanie | Nie rozpoczęto | Pełna macierz baz, testy serwera, awarie, wydajność i staging. |

## Następny zakres po iteracji 2

Rozpocząć E4: wspólny model odczytu dla obu geometrii, resolver przestrzenny, spójna publikacja cache i wersjonowanie API. W tym samym zakresie podłączyć migrację do startu pluginu dopiero po zapewnieniu ochrony chunków. Domknąć zewnętrzną macierz baz E3 przed wydaniem funkcji. `mode: chunks` w `/claim` pozostaje zadaniem E5; geometria i repozytorium same nie oznaczają gotowej funkcji dla graczy.
