# 03 — Dane, transakcje i migracja

[Spis planu](README.md) · [Konfiguracja](04-konfiguracja-i-integracje.md)

## 1. Model trwały

Zachować wspólną tabelę `plots` i identyfikatory działek. Dzięki temu członkowie, flagi i historia nadal odnoszą się do tej samej jednostki. Dodać typ geometrii i tabelę chunków zamiast budowania drugiego, niezależnego systemu własności.

| Element | Planowane znaczenie |
| --- | --- |
| `plots.geometry_type` | `classic` lub `chunks`; istniejące rekordy otrzymują `classic`. |
| `plots.geometry_revision` | Licznik zmian geometrii do walidacji ofert i publikacji cache. |
| `plots.x/y/z` | Dla klasycznych zachowane znaczenie; dla chunkowych punkt startowy teleportacji. |
| `plots.radius` | Ważny tylko dla `classic`; dla `chunks` NULL w schemacie docelowym. |
| `plot_segments` | Wyłącznie rozszerzenia klasyczne. |
| `plot_chunks` | Pełny zbiór chunków działki, łącznie z pierwszym. |
| `plot_expansion_levels` | Liczba skutecznie zakupionych rozszerzeń; wspólna idea, osobne cenniki. |
| `schema_migrations` | Wersje migracji, identyfikator i wynik zastosowania. |
| `plot_operations` | Trwały dziennik operacji wymagających płatności lub odzyskania. |

Docelowy logiczny kształt tabeli chunków:

```sql
CREATE TABLE plot_chunks (
    plot_id INTEGER NOT NULL,
    world_key VARCHAR(255) NOT NULL,
    chunk_x INTEGER NOT NULL,
    chunk_z INTEGER NOT NULL,
    PRIMARY KEY (world_key, chunk_x, chunk_z),
    FOREIGN KEY (plot_id) REFERENCES plots(plot_id) ON DELETE CASCADE
);
CREATE INDEX idx_plot_chunks_plot_id ON plot_chunks(plot_id);
```

To specyfikacja logiczna, nie gotowa migracja dla wszystkich silników. Implementacja musi dopasować typy, indeksy, kolacje, ograniczenia i przebudowę tabel do każdego wspieranego dialektu.

`world_key` jest kanoniczną reprezentacją `plots.world`. Repozytorium musi wymuszać zgodność świata każdego chunka z działką. Tam, gdzie jest to przenośne, zastosować również złożony klucz obcy do wspólnego klucza świata działki. Przy odczycie i imporcie wykrywać osierocone wpisy oraz rozbieżności, zamiast je ignorować.

Dotychczasowe `UNIQUE(x, z, world)` nie reprezentuje nowego modelu: punkt teleportacji nie jest kluczem geometrii. W migracji zastąpić je zwykłym indeksem lokalizacji; wyłączność terenu wymuszać w warstwie operacji i ograniczeniem `plot_chunks`. Klasyczne kolizje nadal wymagają testu geometrycznego. Nie stosować sztucznego `radius = 8` ani promienia 0 dla chunków.

## 2. Niezmienniki danych

1. Działka ma dokładnie jeden typ geometrii, który nie zmienia się podczas zwykłej edycji.
2. Klasyczna działka ma poprawny promień i nie ma rekordów `plot_chunks`.
3. Działka chunkowa ma co najmniej jeden chunk, NULL w promieniu i brak `plot_segments`.
4. Chunki jednej działki leżą w tym samym świecie i tworzą zbiór spójny przez wspólne boki.
5. Żadna para działek, niezależnie od typu i właściciela, nie zajmuje tej samej kolumny X/Z.
6. Liczniki rozszerzeń i historia zmieniają się wyłącznie po skutecznym zapisie.
7. Powierzchnię oblicza się z geometrii. Ewentualny zapis agregatu jest optymalizacją weryfikowaną przy odbudowie.
8. Usunięcie działki usuwa jej geometrię, członków, flagi i poziom rozszerzeń; historia audytowa pozostaje zgodnie z przyjętą polityką retencji.

Nie wszystkie niezmienniki można wyrazić przenośnym `CHECK` w SQL. Pozostałe muszą mieć walidację repozytorium, kontrolę importu i testy integralności.

## 3. Atomowość i konkurencja

Obecne metody atomowego zajmowania i rozszerzania są punktem wyjścia do refaktoryzacji. Nie zakładać, że samo sprawdzenie w GUI albo unikalny klucz chunków rozwiązuje kolizję z geometrią klasyczną.

Dla pierwszej wersji przyjąć jeden serwer zapisujący bazę i wspólny koordynator mutacji przestrzennych. Wszystkie ścieżki tworzenia, rozszerzania, zmiany rozmiaru, transferu, usuwania i importu przechodzą przez ten koordynator. Globalna kolejka mutacji upraszcza egzekwowanie limitów właściciela obejmujących wiele światów. Nie blokuje odczytów ochrony i nie wykonuje SQL na głównym wątku.

Transakcja zajęcia chunka:

1. Zarezerwować operację i obszar; zweryfikować identyfikator żądania przeciw powtórzeniu.
2. Rozpocząć transakcję; ponownie odczytać stan właściciela i działki, jeżeli jest rozszerzana.
3. Sprawdzić sumę działek i powierzchni obu typów, limity chunkowe oraz aktualną rewizję.
4. Sprawdzić kolizję chunk–chunk i chunk–classic na stanie transakcyjnym.
5. Zapisać metadane i chunk, poziom rozszerzeń oraz wpis audytu. Aktualizować rewizję.
6. Zatwierdzić transakcję; opublikować snapshot ochrony według protokołu z dokumentu architektury.
7. Zwolnić rezerwację, zakończyć operację i pokazać wynik.

Naruszenie unikalności docelowego chunka oznacza konflikt, nie ogólny sukces po ponowieniu. Błąd SQL wycofuje całą transakcję. Ponowienia po zakleszczeniu są ograniczone i dotyczą wyłącznie operacji, dla których znany jest stan poprzedniej próby; nie ponawiać automatycznie wypłaty z konta.

Koordynator jest świadomym ograniczeniem pierwszej wersji. Obsługa wielu serwerów wymaga blokad w bazie lub protokołu rezerwacji obejmującego również klasyczne prostokąty i limity właścicieli. Blokada JVM nie zabezpiecza innych procesów. Import wymaga trybu utrzymaniowego bez aktywnych mutacji.

## 4. Płatności

Pierwszy chunk pozostaje bezpłatny, podobnie jak obecne tworzenie działki. Rozszerzanie korzysta z istniejących dostawców ekonomii, ale otrzymuje osobną konfigurację cen. Nie wprowadzać odrębnej waluty ani salda PlotsX.

SQL i dostawca ekonomii nie tworzą jednej transakcji. Plan wymaga dziennika `plot_operations`: ID operacji, aktor, działka lub kandydat, cel, kwota, dostawca, stan płatności, stan zapisu i znaczniki czasu. Stany powinny rozróżniać co najmniej przygotowanie, zlecenie obciążenia, potwierdzone obciążenie, zatwierdzony teren, wymagany zwrot, potwierdzony zwrot i wynik niepewny.

Przebieg: walidacja i rezerwacja → trwałe przygotowanie → obciążenie → zapis terenu → publikacja. Jeśli obciążenie się nie powiedzie, teren nie powstaje. Jeśli zapis się nie powiedzie po obciążeniu, wykonać zwrot przez tego samego dostawcę. Nieudany zwrot wymaga trwałego oznaczenia i raportu dla administratora.

Awaria między odpowiedzią dostawcy a zapisem potwierdzenia może pozostawić niepewny wynik. Bez idempotencji lub historii transakcji dostawcy nie wolno obiecywać rozliczenia dokładnie raz. Takiej operacji nie ponawiamy automatycznie; blokujemy jej dalsze wykonanie i przedstawiamy dane do ręcznego uzgodnienia. Przy restarcie odbudowujemy rezerwacje operacji wymagających wyjaśnienia. Test awarii w tym miejscu jest warunkiem odbioru płatnych rozszerzeń.

## 5. Migracja istniejącej instalacji

1. Zatrzymać przyjmowanie zmian i wykonać spójny backup danych, konfiguracji oraz używanej wersji JAR.
2. Zweryfikować kopię przez próbne odtworzenie przed aktualizacją produkcji.
3. Wykryć bieżący schemat, także na instalacjach bez tabeli wersji migracji.
4. Dodać typ geometrii z wartością `classic` dla wszystkich istniejących działek oraz początkową rewizję.
5. Zmienić nullowalność promienia i indeks lokalizacji; dla silników wymagających przebudowy tabel odtworzyć wszystkie indeksy i relacje.
6. Utworzyć nowe tabele i indeksy, zachowując ID, nazwy, daty, segmenty, członków, flagi i poziomy rozszerzeń.
7. Porównać liczby rekordów, powiązania, obszary ochrony oraz sumy powierzchni przed i po migracji.
8. Zapisać ukończoną wersję migracji dopiero po weryfikacji. Odbudować cache obu typów i uruchomić obsługę graczy.

Migracja ma być odporna na ponowne uruchomienie po przerwaniu. Nie zakładać transakcyjności DDL na każdym silniku; użyć jawnych kroków, znaczników postępu i procedury naprawy. Nieznany typ geometrii lub niespójne dane blokują udostępnienie świata graczom do czasu naprawy. Samo wyłączenie pluginu przy działającym serwerze nie zapewnia ochrony.

## 6. Backup, import i wycofanie

Rozszerzyć `DatabaseSchema`, `SqlBackup` oraz metody eksportu/importu o wszystkie nowe tabele, kolejność zależności i wersję formatu. Eksport ma obejmować również operacje nierozliczone. Import waliduje geometrię, konflikty między typami, świat i klucze obce przed udostępnieniem nowego stanu.

Sprawdzić odtworzenie w tym samym silniku i przenoszenie między wspieranymi dialektami. Stary backup nie zawiera typu: po imporcie wszystkie działki otrzymują `classic`. Nowy backup nie może być przedstawiany jako zgodny ze starym pluginem.

**Wycofanie funkcjonalne:** ustawić `mode: classic` i zrestartować nową wersję. Chunkowe działki nadal są chronione i zarządzalne.

**Wycofanie binarne:** powrót do starego JAR wymaga odtworzenia kompatybilnej kopii bazy i configu. Stary kod nie rozumie nowej geometrii. Cofnięcie kopii usuwa zmiany od czasu backupu, dlatego wymaga zaplanowanego okna utrzymaniowego i uzgodnienia nowych transakcji ekonomicznych. Nie utożsamiać go z bezstratnym przełączeniem trybu.
