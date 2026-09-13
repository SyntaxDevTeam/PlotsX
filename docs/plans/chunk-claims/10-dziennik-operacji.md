# 10 — Trwały dziennik operacji i backup v3

[Postęp](06-postep-implementacji.md) · [Transakcja rozszerzania](09-rozszerzanie-chunkow.md)

## Stan implementacji

Wykonano część E6 B dotyczącą trwałego modelu płatności, migracji, transakcyjnego połączenia z geometrią, odzyskiwania po restarcie i backupu. `OperationJournal` nie wywołuje dostawcy ekonomii. Usługa zakupu, komendy uzgadniania i GUI pozostają kolejnym zakresem; samo istnienie dziennika nie włącza płatnego rozszerzania.

## Dane i migracja

Tabela `plot_operations` zapisuje UUID operacji, ID działki, właściciela, aktora, świat, źródłowy i docelowy chunk, oczekiwaną rewizję, dokładną kwotę dziesiętną, identyfikator dostawcy, walutę, stan i czasy utworzenia/aktualizacji. Kwota jest tekstem dziesiętnym, bez konwersji przez `Double`; repozytorium kontroluje znak, precyzję i skalę.

Migracja geometrii zachowuje wpis `1 / chunk_geometry`. Nowy wpis to `2 / operation_journal`, dodawany dopiero po utworzeniu i zweryfikowaniu tabeli. Start uruchamia obie migracje przed cache i ochroną. Znany marker bez tabeli dziennika jest błędem, a nie przesłanką do cichego utworzenia pustej tabeli. Ponowienie częściowo wykonanej migracji bez znacznika jest obsługiwane.

Dziennik nie ma kaskadowego klucza obcego do `plots`: usunięcie działki nie może usuwać informacji potrzebnej do zwrotu. ID działki w takim wpisie jest historycznym odniesieniem. To zamierzona różnica względem tabel geometrii i członków.

## Stany i dozwolone przejścia

| Stan | Znaczenie | Dalsze przejścia repozytorium |
| --- | --- | --- |
| `PREPARED` | Trwała oferta, dostawca jeszcze nie wywołany | `DEBIT_REQUESTED`, `CANCELLED`; dla kwoty 0 wspólny commit terenu |
| `DEBIT_REQUESTED` | Zapisano zamiar wywołania obciążenia | `DEBITED`, `DECLINED`, `UNCERTAIN` |
| `DEBITED` | Potwierdzono obciążenie | Wspólny commit terenu albo `REFUND_REQUIRED` |
| `LAND_COMMITTED` | Teren i stan operacji zatwierdzono razem | Stan końcowy |
| `REFUND_REQUIRED` | Potrzebne rozliczenie potwierdzonego obciążenia | `REFUND_REQUESTED` |
| `REFUND_REQUESTED` | Zapisano zamiar wywołania zwrotu | `REFUNDED`, `REFUND_REQUIRED`, `UNCERTAIN` |
| `REFUNDED` | Potwierdzono zwrot | Stan końcowy |
| `DECLINED` | Dostawca jednoznacznie odrzucił obciążenie | Stan końcowy |
| `CANCELLED` | Anulowano przed rozpoczęciem obciążenia | Stan końcowy |
| `UNCERTAIN` | Stan zewnętrznego rozliczenia wymaga wyjaśnienia | Brak automatycznego przejścia do ponownej płatności |

Zmiana stanu sprawdza poprzedni stan i niemalejący czas. Nieaktualne porównanie zwraca `false`; niedozwolone przejście jest błędem. Przygotowanie i zmiany statusów płatności wymagają połączenia auto-commit, aby przyszły wywołujący nie wykonał zewnętrznej operacji przed utrwaleniem zamiaru.

Ponowne przygotowanie identycznego ID zwraca `false`, również po późniejszej zmianie stanu. Nie oznacza to zgody na ponowne obciążenie. Użycie tego samego ID dla innego żądania jest odrzucane. Nowa operacja jest blokowana, jeżeli istnieje nierozliczona operacja tego właściciela lub działki. Wywołujący musi serializować zapisy wspólnym koordynatorem; nie jest to wieloserwerowa blokada SQL. Na obecnym etapie reguła dotyczy przygotowania operacji w dzienniku, nie wszystkich starych komend administracyjnych.

## Zatwierdzanie geometrii

`applyLand()` wymaga istniejącej transakcji i potwierdzonego `DEBITED`, z wyjątkiem bezpłatnego `PREPARED`. Sprawdza świat, odtwarza kierunek z zapisanych chunków i wywołuje `ChunkExpansionTransaction.applyInTransaction()` z zapisaną ofertą oraz bieżącymi limitami.

Po sukcesie stan `LAND_COMMITTED` jest zapisywany w tej samej transakcji co chunk, rewizja, poziom rozszerzeń i historia. Wywołujący musi wykonać rollback po odmowie lub wyjątku. Testy obejmują zarówno awarię późnego zapisu geometrii, jak i odmowę aktualizacji dziennika po zapisaniu chunka. W obu przypadkach rollback przywraca teren oraz stan `DEBITED`.

Zewnętrzne obciążenie nie jest częścią transakcji JDBC. Ten kontrakt nie obiecuje rozliczenia dokładnie raz; wymaga poprawnej klasyfikacji wyników dostawcy przez przyszłą usługę zakupu.

## Restart i import

Przy starcie, przed przyjmowaniem nowych operacji:

- `PREPARED` przechodzi do `CANCELLED`, ponieważ nie zapisano zamiaru wywołania dostawcy.
- `DEBIT_REQUESTED` i `REFUND_REQUESTED` przechodzą do `UNCERTAIN`.
- `DEBITED` przechodzi do `REFUND_REQUIRED`: brak wspólnego commit terenu i stanu oznacza, że zakup nie został zakończony.
- Pozostałe nierozliczone i końcowe stany pozostają zachowane.

Mechanizm nie pobiera ani nie zwraca pieniędzy. Nierozliczone wpisy są raportowane w logu przez ID operacji/działki, właściciela, dostawcę, kwotę, walutę i stan. Ponowne uruchomienie recovery nie zmienia ponownie sklasyfikowanych wpisów.

Import wymaga innej ostrożności niż zwykły restart. Backup może pochodzić sprzed późniejszego rozliczenia u dostawcy. Dlatego wszystkie importowane stany niekońcowe otrzymują `UNCERTAIN` w tej samej transakcji co odtworzone dane. Nie są automatycznie wznawiane ani anulowane jako rzekomo niewysłane. Należy zachować oryginalny backup do porównania z historią dostawcy.

## Backup v3

Eksport schematu z dziennikiem tworzy nagłówek `PlotsX SQL backup v3`. Obejmuje wszystkie operacje, również nierozliczone i odnoszące się do usuniętych działek. Eksport waliduje rekordy dziennika w swoim snapshotcie SQL.

Import v3 waliduje geometrię i dziennik przed commit, a następnie obejmuje nierozliczone operacje opisaną wyżej kwarantanną. Nieznany stan lub niepoprawne dane powodują rollback. Import dowolnej wersji odmawia nadpisania bazy zawierającej bieżące nierozliczone operacje. Terminalne dane dziennika mogą zostać zastąpione jako część autoryzowanego pełnego odtworzenia.

Formaty v1 i v2 pozostają obsługiwane. Ich import do nowego runtime zachowuje tabelę dziennika, lecz odtwarza brak operacji finansowych, o ile nie ma nierozliczonych wpisów blokujących import. Eksport starszego schematu bez dziennika nadal daje odpowiednio v1/v2. Nie zmieniono zamrożonych schematów tych formatów.

## Testy i pozostały zakres

`./gradlew test shadowJar --offline --console=plain` — **122 testy, 0 niepowodzeń, 0 błędów, 0 pominiętych; plugin zbudowany**.

Dodano 15 testów `OperationJournalTest` na SQLite i H2. Obejmują migrację/powtórzenie, brak tabeli mimo znacznika, zachowanie kwoty, ponowione ID, blokadę nierozliczonego właściciela/działki, porównanie stanów, restart w oknach obciążenia/zwrotu, wspólny commit i rollback, bezpłatny zakup, niepotwierdzoną płatność, przechowanie wpisu po usunięciu działki, cztery kombinacje backupów v3, błędny import oraz zgodność v1/v2 z nowym runtime.

W tej części nie wykonywano ponownie zewnętrznej macierzy MySQL/MariaDB/PostgreSQL ani testów Paper. Historyczne wyniki E3/E4 nie są deklaracją sprawdzenia nowej migracji finansowej na tych silnikach.

Pozostało: usługa zakupu i osobny cennik, podłączenie rzeczywistych dostawców ekonomii z bezpiecznym wątkiem wywołania, spójna rezerwacja od walidacji oferty do publikacji, narzędzie uzgadniania z audytem administratora, GUI/dialogi i scenariusze awarii z dostawcą. Chunkowe GUI rozszerzania nadal jest nieaktywne.
