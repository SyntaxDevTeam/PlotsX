# 08 — Raport weryfikacji integracji chunków

[Spis planu](README.md) · [Postęp](06-postep-implementacji.md)

Data: 13 września 2026 r. Raport rozdziela ukończone sprawdzenia E3/E4 od akceptacji całego wydania E8.

## Testy automatyczne i macierz SQL

Wynik końcowy: **95 testów, 0 niepowodzeń, 0 błędów, 0 pominiętych**. [Zestawienie zestawów testowych](evidence/sql-matrix.json).

| Silnik | Wersja sprawdzona | Izolacja |
| --- | --- | --- |
| SQLite | JDBC 3.53.4.0 | osobne bazy testowe |
| H2 | 2.4.240 | losowe bazy w pamięci |
| MariaDB | 11.8.6 | osobna instancja localhost; losowa baza na scenariusz |
| MySQL | 8.4.11 | rzeczywisty MySQL, nie zamiennik MariaDB; osobna instancja |
| PostgreSQL | 18.6 | osobna instancja; losowy schemat na scenariusz |

Test `ChunkPersistenceTest` wykonuje scenariusze na wszystkich skonfigurowanych silnikach. Backup mieszanych danych sprawdza **25 par źródło–cel**. Weryfikacja obejmuje migrację v1, wznowienie migracji częściowej, zachowanie danych/powiązań/licznika ID, null promienia chunków, znormalizowany świat, kaskadę usuwania, savepoint, rollback i odrzucanie błędnych geometrii/importów. Sprawdzono usunięcie baz i schematów `plotsx_test_*` po zakończeniu.

`ClaimTransactionTest` sprawdza dodatkowo limity pola i chunków, kolizje mieszane w obu kierunkach, rollback po awarii flag oraz transfer własności. Test konkurencji z latchami i wspólnym koordynatorem wymusza start dwóch zapisów: tylko jeden wygrywa przy przecięciu geometrii albo jednym wolnym miejscu w limicie właściciela.

`ProtectionCoordinatorTest` sprawdza odmowę podczas publikacji, błąd publikacji i recovery, publikację po rollbacku oraz zagnieżdżone wywołania. Dotychczasowe testy cache obejmują spóźnione odczyty, epoki, izolację snapshotów i niezmienność kolekcji.

Powtórzenie macierzy wymaga wcześniej uruchomionych **wyłącznie testowych** instancji. Harness używa kont bez hasła w odizolowanych lokalnych instancjach; nie wskazywać istniejącej bazy produkcyjnej.

```bash
PLOTSX_TEST_MARIADB_URL=jdbc:mariadb://127.0.0.1:13376/ \
PLOTSX_TEST_MYSQL_URL=jdbc:mariadb://127.0.0.1:13377/ \
PLOTSX_TEST_POSTGRESQL_URL='jdbc:postgresql://127.0.0.1:15432/postgres?user=plotsx' \
./gradlew test --offline --console=plain --rerun-tasks
```

Sterownik MariaDB JDBC obsługuje tutaj połączenie do rzeczywistego MySQL; tożsamość silnika zweryfikowano przez `SELECT VERSION()`. Bez zmiennych środowiskowych uruchamiają się tylko SQLite/H2. `--rerun-tasks` jest wymagane po zmianie macierzy, ponieważ same zmienne nie stanowią wejść zadania Gradle.

## API Java i Kotlin

Sprawdzono oba typy snapshotów, dokładną powierzchnię, granice ujemne, wolny narożnik i nullable promień. Osobny test kompiluje konsumenta starego kontraktu `getRadius(): int`, następnie uruchamia go z v2 i potwierdza oczekiwany `NoSuchMethodError`. Jest to dowód jawnej zmiany binarnej, a nie deklaracja zgodności wstecznej. [Procedura migracji konsumentów](07-api-v2-i-uruchomienie.md).

## Test na działającym Paper

Platforma: **Paper 1.21.11 build 132**, Java 21, osobny świat, SQLite i port wyłącznie localhost. Artifact Paper ma SHA-256 `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba`.

Harness znajduje się w `src/liveTest/java/pl/syntaxdevteam/plotsx/testing/PaperAcceptance.java`; buduje go `./gradlew liveTestJar`. Jest osobnym pluginem akceptacyjnym i **nie trafia do JAR PlotsX**. Uruchamia zdarzenia przez rzeczywisty `PluginManager`, na prawdziwych blokach świata. Aktorzy są kontrolowanymi proxy interfejsu `Player`; nie jest to sesja klienta Minecraft ani test pakietów/dialogów.

Sprawdzenia:

- start pluginu i migracja przed uruchomieniem ochrony;
- rejestracja usługi API v2;
- wczytanie obu geometrii, pełne chunki przy współrzędnych ujemnych, wolny narożnik;
- odmowa niszczenia przez gościa i zgoda dla właściciela dla obu typów;
- zgodność API z decyzją listenera;
- zgoda na niszczenie w wolnym narożniku i odmowa ruchu tłoka przez krawędź chunka;
- odmowa przepływu płynu przy fladze blokującej;
- kontrolowane okno publikacji: odmowa dla rzeczywistego zdarzenia i `evaluateFlag()`;
- zajęcie według aktywnego trybu, natychmiastowy odczyt wyniku, usunięcie i natychmiastowe zwolnienie terenu;
- restart z mieszaną bazą, odrzucenie zmiany strategii przez reload oraz zachowanie obu typów po prawidłowym reloadzie.

Na tej samej bazie zakończyły się powodzeniem kolejne uruchomienia `classic → chunks → classic`. Obie geometrie zachowały ochronę, a nowy zapis wybierał aktualnie skonfigurowaną strategię. Logi końcowych przebiegów nie zawierały wyjątków ani błędów listenerów.

Wyniki zapisano w [evidence/paper-chunks.txt](evidence/paper-chunks.txt) i [evidence/paper-classic.txt](evidence/paper-classic.txt). Harness zapisuje `RESULT PASS` dopiero po wszystkich asercjach i zatrzymuje serwer. Należy dodatkowo sprawdzić log serwera pod kątem wyjątków listenerów, ponieważ Bukkit może je przechwytywać.

Odtwarzanie: zbudować `shadowJar`, `apiJar`, `liveTestJar`, skopiować JAR główny i `-live-test.jar` do `plugins/` osobnego serwera Paper, ustawić port/IP lokalny i użyć zatwierdzonej konfiguracji EULA. Pierwszy przebieg wymaga pustej bazy. Kolejne, z tą samą bazą zawierającą dwie działki testowe, uruchamiać z `-Dplotsx.acceptance.reuse=true`, zmieniając `plots.claiming.mode` pomiędzy zatrzymanymi uruchomieniami. Wynik jest w `plugins/PlotsXAcceptance/result.txt`.

## Wydajność resolvera i pamięć

[Surowy raport benchmarku](evidence/benchmark.md). Syntetyczne zbiory: 1 tys., 10 tys. i 100 tys. jednochunkowych działek oraz 101 działek klasycznych, w tym duża geometria korzystająca z fallbacku. Zbiór klasyczny umieszczono osobno przestrzennie, aby kontrolowane trafienia/pudła chunków pozostawały jednoznaczne.

100 tys. wywołań rozgrzewki, następnie 50 tys. deterministycznych odczytów na wielkość zbioru; pomiar pojedynczego resolvera bez SQL i bez ładowania światów/chunków. Pamięć to przybliżony przyrost zajętego heap po GC: zamrożone modele i indeks, bez wejściowej kolekcji.

| Chunki | Budowa snapshotu | Przybliżony heap | p50 | p95 | p99 |
| --- | --- | --- | --- | --- | --- |
| 1 000 | 12,24 ms | 769 696 B | 149 ns | 237 ns | 348 ns |
| 10 000 | 22,37 ms | 4 477 176 B | 136 ns | 407 ns | 1 153 ns |
| 100 000 | 126,87 ms | 46 388 496 B | 291 ns | 543 ns | 679 ns |

JVM benchmarku: OpenJDK 21.0.12.1, Linux amd64, 16 procesorów logicznych widocznych przez JVM. Wywołanie:

```bash
PLOTSX_BENCHMARK=1 ./gradlew test --tests '*ChunkBenchmarkTest' --offline --console=plain --rerun-tasks
```

Brak progu czasowego w JUnit jest celowy: lokalny pomiar nie powinien tworzyć niestabilnego testu CI. Nie jest to pomiar TPS, pełnego czasu startu pluginu, czasu SQL ani kosztu płatnych rozszerzeń. Nie deklarujemy na tej podstawie gotowości produkcyjnej ani spełnienia budżetu pamięci konkretnego serwera.

## Pozostała akceptacja wydania

E5/E7: klient Minecraft i oba UI, pełny scenariusz komendy, ruch/podwójne potwierdzenie, WorldGuard jako zainstalowany plugin, pozostałe hooki i języki. E6: rozszerzenia chunkowe, płatności, dziennik i recovery. E8: docelowe obciążenie i budżety, porównanie klasycznego resolvera sprzed zmian, pozostałe wspierane wersje serwera, staging i odtwarzanie całego wydania po rollbacku.
