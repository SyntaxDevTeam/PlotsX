# 05 — Realizacja, testy i wdrożenie

[Spis planu](README.md)

Bieżący stan prac: [rejestr implementacji z wykonanymi elementami i wynikami testów](06-postep-implementacji.md). Poniższa lista gotowości dotyczy kompletnej funkcji, a nie samego fundamentu geometrii.

## 1. Kolejność implementacji

Etapy są zależnościami technicznymi, nie deklaracją terminów. Każdy powinien stanowić osobny, możliwy do przeglądu zakres zmian. Nie udostępniać `chunks` produkcyjnie przed ukończeniem ochrony, migracji i backupu.

| Etap | Zakres | Kryterium zakończenia |
| --- | --- | --- |
| E1 — Kontrakty | Zatwierdzenie reguł, semantyki configu, limitów, wersjonowania API i modelu jednego serwera | Brak sprzeczności między komendami, ochroną i zapisanym typem. |
| E2 — Geometria | Wydzielenie `ClassicGeometry`, implementacja `ChunkGeometry`, przecięcia i obszary | Testy klasyczne przechodzą bez zmiany granic; chunk ma dokładnie 256 kolumn. |
| E3 — Persystencja | Schemat, migracje, repozytorium, odczyty obu typów, eksport/import | Odtworzenie na pięciu silnikach i brak utraty danych klasycznych. |
| E4 — Ochrona | Resolver, indeksy, rewizje, publikacja, listener i API | Obie geometrie chronione również po restartach i błędach publikacji. |
| E5 — Zajmowanie | Parser configu, strategie, wspólne limity, transakcja, potwierdzenie | `/claim` tworzy właściwy typ i odrzuca równoległe konflikty. |
| E6 — Cykl życia | Rozszerzanie, transfer, usunięcie, płatności, dziennik odzyskiwania | Brak częściowych działek i nieraportowanych rozliczeń. |
| E7 — Interfejsy | GUI, dialogi, granice, tłumaczenia, hooki, dokumentacja publiczna | Obie ścieżki UI dają identyczne wyniki dla obu typów. |
| E8 — Wydanie | Regresja, próby awarii, wydajność, staging i procedura wycofania | Spełniona lista gotowości poniżej. |

E2 powinien najpierw oddzielić obecną matematykę od komend bez wprowadzania nowych zachowań gracza. Dzięki temu późniejsze błędy można odróżnić od regresji refaktoryzacji. E3 i E4 mogą używać danych testowych chunków, zanim E5 udostępni ich tworzenie.

## 2. Testy jednostkowe

Rozszerzyć istniejące `PlotGeometryTest`, `OwnershipTransferTest`, `SqlBackupTest` i `ExpansionPricingTest` w zakresie odpowiadającym ich odpowiedzialności. Dodać testy czystej geometrii chunkowej i polityki limitów; nie uzależniać ich od uruchomionego serwera.

| Obszar | Przypadki wymagane |
| --- | --- |
| Współrzędne | -17, -16, -1, 0, 15, 16; oba wymiary; ujemne pozycje ułamkowe; granice zakresów. |
| Powierzchnia | Jeden chunk = 256; duplikaty nie zwiększają powierzchni; sumy mieszane; przepełnienie. |
| Przynależność | Pierwszy i ostatni blok chunka, blok obok, pusta dziura i wklęsły kształt. |
| Sąsiedztwo | Cztery kierunki, odrzucenie narożnika, odrzucenie istniejącego i obcego chunka. |
| Kolizje | Wszystkie pary geometrii, przecięcie jednym blokiem, stykanie bez przecięcia, różne światy. |
| Config | Brak klucza, poprawne tryby, literówka, NULL, błędna sekcja, 0, liczby ujemne i przepełnione. |
| Limity | Wartość dokładnie na granicy, przekroczenie o jeden chunk, najwyższe uprawnienie, limit globalny światów. |
| Ceny | Pierwszy zakup, mnożnik 1, cena 0, niepoprawna cena, nadmierna kwota, zmiana configu. |
| Oferta | Podwójne potwierdzenie, ruch w chunku i poza nim, zmiana właściciela, rewizji i uprawnień. |

## 3. Testy integracyjne bazy

Macierz obowiązkowa: SQLite, MySQL, MariaDB, PostgreSQL i H2. Dla każdego silnika sprawdzić pustą instalację, migrację klasycznych działek z rozszerzeniami, mieszany zbiór danych, restart, eksport, import i kaskadowe usunięcie. Przenoszenie backupów między silnikami objąć pełną macierzą źródło–cel w automatyzacji wydania lub równoważnym, udokumentowanym pokryciem wszystkich dialektów eksportu i importu.

Testy konkurencji uruchamiać z kontrolowanymi punktami synchronizacji, aby rzeczywiście wymusić konflikt:

- Dwaj gracze zajmują ten sam chunk: dokładnie jeden sukces, jeden rekord geometrii.
- Klasyczne zajęcie i chunkowe zajęcie przecinają się: dokładnie jeden sukces.
- Dwa rozszerzenia tej samej działki na ten sam cel: jeden zakup i jeden przyrost licznika.
- Jeden właściciel zajmuje dwa odległe tereny mając miejsce tylko na jeden: limit nie zostaje przekroczony.
- Transfer do odbiorcy konkuruje z jego nowym zajęciem: wspólne limity nadal obowiązują.
- Usunięcie konkuruje z potwierdzeniem rozszerzenia: brak osieroconego chunka i obciążenia za nieistniejący teren.

Import celowo uszkodzonych danych musi wykrywać: nieznany typ, pustą geometrię chunkową, duplikaty, niespójny świat, rozłączną działkę, segment przy typie `chunks` i kolizję mieszaną. Nie akceptować częściowego importu jako sukcesu.

## 4. Testy na serwerze

Przeprowadzić scenariusze dla obu trybów konfiguracji i obu zapisanych typów, w GUI legacy oraz natywnych dialogach tam, gdzie są dostępne. Sprawdzić obsługiwane przez projekt wersje serwera wskazane w konfiguracji budowania i wydania; nie deklarować nowej zgodności platformowej bez testu.

Zakres: tworzenie, rozszerzanie, nazwa, lista, teleport, członkowie, role, flagi, transfer, usunięcie, wizualizacja, aliasy komend i prywatne kontenery. Dla ochrony sprawdzić właściciela, członka, osobę obcą i działania środowiska na granicach classic–chunks, chunks–chunks oraz chunks–teren wolny.

WorldGuard: brak pluginu, region na części chunka, region przy granicy, błąd odczytu regionu. Ekonomia: brak dostawcy, brak środków, poprawne pobranie, błąd zapisu po pobraniu, udany i nieudany zwrot. Światy: niedozwolony, wyładowany, ponownie załadowany, nazwy z różną wielkością liter.

## 5. Próby awarii i spójność ochrony

Wstrzyknąć błąd przed zapisem, między rekordem działki i geometrią, przed commit, po commit przed publikacją cache, podczas odświeżenia cache oraz w każdym przejściu płatności. Po restarcie porównać SQL, indeks przestrzenny, API i faktyczną ochronę.

Wymagany wynik: brak częściowego zajęcia, brak niezabezpieczonego zatwierdzonego terenu, brak cichego usunięcia geometrii i jawna lista operacji finansowych wymagających wyjaśnienia. Starszy pełny refresh cache nie może przywrócić usuniętej działki ani zgubić nowego chunka.

Przerwać każdą migrację w obsługiwanym punkcie DDL i sprawdzić ponowne uruchomienie. Próba rollbacku musi zostać przeprowadzona na kopii, obejmując config, bazę i wersję pluginu.

## 6. Wydajność i obserwowalność

Przygotować dane o 1 tys., 10 tys. i 100 tys. chunków oraz mieszankę klasycznych działek, w tym dużych segmentów. Dla każdego zestawu zmierzyć pamięć indeksu, czas startu, p50/p95/p99 resolvera, czas publikacji mutacji, zajmowania i rozszerzania. Porównać tryb klasyczny przed i po refaktoryzacji na tym samym sprzęcie, JVM i obciążeniu.

Kryteria techniczne: brak zapytań SQL i ładowania chunków w zwykłym wyszukiwaniu ochrony; brak liniowego skanowania wszystkich chunkowych działek; ograniczony koszt wizualizacji; brak regresji klasycznego resolvera większej niż 10% p95 w ustalonym benchmarku bez wyjaśnienia i akceptacji technicznej. Budżet pamięci i czasu startu ustalić dla docelowego serwera przed E8, zapisując liczby i środowisko w raporcie pomiarowym.

Log startowy zawiera wersję schematu, aktywny tryb, liczbę działek każdego typu, liczbę chunków i wynik odbudowy indeksu. Log mutacji zawiera ID operacji, ID działki, aktora, typ, cel, wynik i czas. Osobno raportować niespójność geometrii, konflikty, opóźnioną publikację oraz nierozliczone płatności. Nie logować danych dostępowych bazy.

## 7. Rejestr ryzyk

| Ryzyko | Skutek | Ograniczenie i dowód |
| --- | --- | --- |
| Chunk zakodowany promieniem | Błędna ochrona granic | Osobna geometria, test dokładnie 256 kolumn. |
| Config filtruje ochronę | Stare tereny tracą ochronę | Macierz przełączeń i resolver obu typów. |
| Unikalność SQL sprawdza tylko chunki | Kolizja z klasyczną działką | Wspólny koordynator i test konkurencji mieszanej. |
| API nadal sugeruje kwadrat | Błędne decyzje integracji | Wersjonowany kontrakt i test konsumentów. |
| Stary refresh nadpisuje mutację | Nieaktualna ochrona | Rewizje i test wymuszonej kolejności publikacji. |
| Awaria po pobraniu opłaty | Utrata środków lub podwójne pobranie | Trwały dziennik, brak ponowień wyniku niepewnego. |
| Niepełna migracja/import | Utrata lub pominięcie działek | Test przerwania i odtworzenie na każdym silniku. |
| Nieograniczony indeks lub cząsteczki | Nadmierna pamięć albo obciążenie | Limity pracy i benchmark skrajnych geometrii. |

## 8. Procedura wydania

1. Przygotować wydanie testowe z `mode: classic` oraz notatką o zmianie schematu i API.
2. Odtworzyć kopię produkcji na środowisku testowym; porównać geometrię i metadane przed migracją i po niej.
3. Uruchomić `chunks`, wykonać pełny cykl działki, przełączyć z powrotem na `classic` i sprawdzić oba typy.
4. Zaktualizować integracje wymagające API V2; przeprowadzić próby płatności i restartu.
5. Przygotować zweryfikowany backup produkcji oraz okno utrzymaniowe; wdrożyć nową wersję początkowo w trybie klasycznym.
6. Po kontroli logów, migracji i ochrony włączyć `chunks` przez config i restart.
7. Obserwować konflikty, limity, opóźnienia i operacje finansowe. W razie potrzeby wyłączyć nowe chunkowe zajęcia przez powrót do `classic` na nowej wersji.

## 9. Lista gotowości

- [ ] Domyślny `classic` zachowuje dotychczasową geometrię i dane.
- [ ] Oba typy są chronione i zarządzalne w obu trybach configu.
- [ ] Wszystkie odczyty geometrii, sumy powierzchni i transfery obsługują oba typy.
- [ ] Nowe zajęcia i rozszerzenia nie mogą ominąć limitów ani kolizji.
- [ ] Migracja i odtworzenie przeszły macierz wspieranych baz.
- [ ] Rozstrzygnięto kompatybilność API i przetestowano konsumentów.
- [ ] GUI oraz dialogi korzystają ze wspólnego kontraktu operacji.
- [ ] Płatności i przypadki niepewne mają trwały ślad i procedurę naprawy.
- [ ] Wykonano pomiary, testy awarii i próbę wycofania.
- [ ] Uzupełniono `docs/commands.md`, `docs/limits-and-worlds.md`, `docs/api.md` i odpowiednie strony `docs/wiki/`, wyraźnie oznaczając wersję wprowadzającą funkcję.
- [ ] Przetestowano tłumaczenia EN, PL, ES, FR i DE oraz komentarze configu.

Dokumentację użytkową aktualizujemy przy implementacji. Niniejszy plan pozostaje oddzielony od instrukcji dostępnych dziś funkcji, aby administrator nie uznał proponowanych kluczy za już działające.
