# 09 — Rozszerzanie chunków: transakcja geometrii

> Aktualizacja 20.09.2026: zakup chunków z ekonomią, GUI ekwipunkowym i natywnym dialogiem jest podłączony — [iteracja 7 i aktualny zakres](11-zakup-chunka-i-gui.md). Opis E6 A poniżej zachowuje historię warstwy geometrii.

[Postęp](06-postep-implementacji.md) · [Plan danych i płatności](03-dane-transakcje-migracja.md)

## Wykonany zakres E6 A

`ChunkExpansionTransaction` realizuje zapis pojedynczego rozszerzenia. Nie jest usługą zakupu i nie wykonuje wywołań Bukkit ani ekonomii. Punkt wejścia w `DatabaseHandler` jest wewnętrzny i korzysta z istniejącego `ProtectionCoordinator`, dzięki czemu wynik jest zwracany po publikacji cache. Klasyczne rozszerzanie pozostaje osobną ścieżką; odrzuca dane chunkowe przed odczytem promienia.

Oferta zawiera ID działki, oczekiwanego właściciela, aktora audytu, źródłowy chunk, kierunek, oczekiwany cel i rewizję geometrii. Właściciel i aktor są osobnymi polami celowo: warstwa uprawnień ustala, kto może działać, a transakcja kontroluje, do kogo działka nadal należy.

Kolejność walidacji i zapisu:

1. Odczyt zapisanych geometrii i właścicieli w transakcji `SERIALIZABLE`.
2. Kontrola istnienia działki, właściciela, typu i rewizji; przepełnienie rewizji jest odrzucane.
3. Wyznaczenie sąsiada wskazanego źródła; odrzucenie obcego źródła, zajętego celu, skoku i rozbieżności oferty.
4. Kontrola limitu chunków działki, wszystkich chunków właściciela i powierzchni obu typów we wszystkich światach.
5. Kolizje celu z pozostałymi działkami w tym samym kanonicznym świecie, niezależnie od właściciela.
6. Warunkowa aktualizacja rewizji z kontrolą ID/właściciela/typu/starej rewizji, zapis jednego chunka, poziomu i wpisu historii.
7. Wspólny commit; przy odrzuceniu lub wyjątku rollback. Publikacja snapshotu następuje w koordynatorze `DatabaseHandler` przed zwrotem wyniku.

Zwracane wyniki rozróżniają brak działki, zmianę właściciela, niewłaściwy typ, nieaktualną ofertę, nieprawidłowy cel, trzy rodzaje limitów i kolizję. Wyjątek SQL pozostaje wyjątkiem, aby przyszła usługa zakupu mogła rozróżnić awarię zapisu od odmowy walidacji.

## Licznik, rewizja i historia

Po sukcesie powierzchnia rośnie o 256 kolumn, rewizja o jeden, a poziom rozszerzeń o jeden. Nie tworzy się nowego ID działki, właściciela, flag ani członków. Cel musi być sąsiadem wspólnym bokiem; wklęsłe kształty i wolne dziury nadal są dozwolone.

Wykorzystano istniejącą tabelę `plot_expansion_levels`. Nie dodano migracji ani nowego formatu backupu, ponieważ aktualny backup już obejmuje tę tabelę. Jeżeli starszy import chunków nie zawiera licznika, punktem wyjścia jest `liczba istniejących chunków - 1`. To reguła inicjalizacji przyszłego poziomu cenowego, nie dowód wcześniejszych płatności. Istniejący nieujemny licznik jest zachowany; ujemny lub przepełniony blokuje zapis.

Wpis historii ma postać `EXPAND_CHUNK:<kierunek>:<chunkX>,<chunkZ>:<rewizja>` i zapisuje aktora oraz czas. Jest częścią tej samej transakcji co teren i licznik. Nie jest dziennikiem finansowym ani identyfikatorem idempotentnej płatności.

## Dwa kontrakty transakcyjne

`expand()` przyjmuje dedykowane połączenie auto-commit, rozpoczyna transakcję, zatwierdza wyłącznie sukces i odtwarza stan połączenia. `applyInTransaction()` wymaga już otwartej transakcji i pozostawia commit/rollback wywołującemu. Drugi wariant służy do przyszłego zapisu stanu `LAND_COMMITTED` w tym samym commit co geometria. Po odrzuceniu lub wyjątku wywołujący musi wycofać całą transakcję.

Żaden wariant nie zastępuje blokady JVM dla wspólnych limitów i klasycznych kolizji. Produkcyjny punkt wejścia pozostaje objęty wspólnym koordynatorem. Współdzielenie zapisów przez kilka serwerów jest poza kontraktem.

## Weryfikacja

Weryfikacja: `./gradlew test --offline --console=plain` — **107 testów, 0 niepowodzeń, 0 błędów, 0 pominiętych**.

Dodano 12 testów `ChunkExpansionTransactionTest`. Scenariusze zapisu działają na SQLite i H2; test konkurencji używa dwóch połączeń H2 i latchy. Sprawdzono:

- dokładne granice dodatnio-ujemne, powierzchnię, rewizję, licznik i aktora historii;
- nieaktualne i powtórzone oferty, brak działki, zmianę właściciela i typ klasyczny;
- obce źródło, narożnik, przeskok i rozbieżny cel;
- limit działki, właściciela i mieszaną powierzchnię w drugim świecie, także dokładną granicę limitu;
- kolizję klasyczną na jednej kolumnie, różną wielkość liter świata i drugą działkę tego samego właściciela;
- rollback po awarii późnego zapisu historii oraz rollback transakcji wywołującego;
- inicjalizację licznika starszego importu i przepełnienie rewizji;
- dwie równoległe oferty tej samej rewizji: dokładnie jeden sukces, jeden przyrost licznika i prawidłowy opublikowany cache.

Ta iteracja nie dodaje nowych wyników testów zewnętrznych silników, płatności, GUI ani serwera Paper. Wcześniejsza macierz E3/E4 pozostaje historycznym wynikiem swojego zakresu.

## Zrealizowana integracja E6 B/C

Trwały dziennik, migracja, backup v3, zachowany dostawca i waluta, klasyfikacja
wyników niepewnych, obsługa restartu oraz kompensacja są wykonane —
[opis dziennika](10-dziennik-operacji.md). GUI i dialog ponownie sprawdzają
uprawnienia, położenie, cenę, limity, rewizję oraz dokładny region WorldGuard,
a następnie wywołują wspólną usługę zakupu opisaną w dokumencie 11.
