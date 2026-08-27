# Ocena stopnia ukończenia pluginu PlotsX

## Podsumowanie

Plugin oceniam na około **55–60% gotowości do stabilnego wydania**.

Jak na wersję **1.0.0-Alpha-4**, projekt jest dość zaawansowany funkcjonalnie. Zawiera przepływ claim/unclaim, GUI, obsługę kilku baz danych, cache, flagi ochrony oraz integrację z CoreProtect. Nie nadaje się jednak jeszcze do wdrożenia na publicznym serwerze bez poprawek bezpieczeństwa. Najpoważniejszym aktualnym problemem jest możliwość usunięcia cudzej działki przez zwykłego gracza.

Polecenie:

```text
./gradlew --no-daemon clean build
```

kończy się powodzeniem. Projekt nie zawiera jednak testów (`NO-SOURCE`). Kompilator zgłasza sześć ostrzeżeń, a Shadow dodatkowe ostrzeżenia dotyczące przetwarzania metadanych Kotlina.

## Stan głównych obszarów

| Obszar | Ocena | Uwagi |
|---|---:|---|
| Kompilacja i pakowanie | 85% | Build działa i powstaje Shadow JAR |
| Claim/unclaim | 40% | Krytyczne błędy właściciela i uprawnień |
| Zarządzanie działką i GUI | 65% | Flagi, lista, teleport i rename istnieją; expand nie jest zaimplementowany |
| Ochrona terenu | 70% | Rozbudowany listener, ale kilka klasycznych luk griefingu |
| Baza danych | 75% | SQLite, H2, MySQL/MariaDB i PostgreSQL; brak testów migracji |
| Cache | 65% | Działa, ale odświeżanie jest kosztowne i miejscami dublowane |
| Paper | 65% | Kod się kompiluje, lecz brakuje testu uruchomieniowego |
| Folia | 25% | Zadeklarowane wsparcie nie odpowiada używanemu schedulerowi |
| Integracje | 45% | CoreProtect częściowo; PlaceholderAPI jest zakomentowane |
| Testy i CI | 5% | Brak testów i widocznej automatyzacji |
| Dokumentacja/release | 15% | README ma tylko dwa wiersze |

## Priorytet krytyczny — przed publicznymi testami

> **Stan po przeglądzie:** punkty 1 i 2 są świadomymi odstępstwami na czas jednoosobowych testów działek. Nie zostały zmienione. Punkty 3–5 zostały wdrożone: komendy bezpiecznie odrzucają konsolę, konfiguracja świata i limitu jest egzekwowana, a końcowy claim ponownie sprawdza limit oraz kolizję w transakcji.

### 1. Przywrócenie kontroli właściciela i uprawnienia w `/unclaim`

**Status: celowo odłożone na czas jednoosobowych testów.**

W `UnclaimCMD.kt` sprawdzenie uprawnienia oraz właściciela działki jest zakomentowane. Każdy gracz stojący na działce może obecnie otworzyć ekran potwierdzenia i usunąć tę działkę.

Sugestie:

- przywrócić sprawdzenie `PermissionChecker.canUnclaimPlot`;
- sprawdzać, czy wykonujący polecenie jest właścicielem;
- dopuścić administratora wyłącznie przez jawne uprawnienie `ADMIN_BYPASS`;
- ponownie zweryfikować działkę i właściciela bezpośrednio przed usunięciem;
- dodać test regresyjny potwierdzający, że obcy gracz nie może usunąć działki.

### 2. Usunięcie testowych danych właściciela z claimowania

**Status: celowo odłożone na czas jednoosobowych testów.** Wpis dziennika utworzenia przechowuje jednak rzeczywisty UUID gracza wykonującego operację.

`ClaimCMD.kt` przypisuje obecnie każdą tworzoną działkę użytkownikowi `yRoshee` i tworzy nazwę `Działka yRoshee N`.

Kod powinien używać rzeczywistego gracza, na przykład:

```kotlin
val uuid = p.uniqueId
val plotName = "${p.name}_Plot_$nextNum"
```

### 3. Naprawienie komend wykonywanych z konsoli

**Status: wykonane.** `/claim` i `/unclaim` używają bezpiecznego rzutowania nadawcy i zwracają komunikat `error.console` dla konsoli.

`/claim` oraz `/unclaim` rzutują nadawcę na `Player` przed sprawdzeniem jego typu. Wywołanie tych komend z konsoli może zakończyć się `ClassCastException`.

Najpierw należy zastosować bezpieczne rzutowanie albo warunek:

```kotlin
val player = stack.sender as? Player ?: run {
    stack.sender.sendMessage(/* komunikat */)
    return
}
```

### 4. Egzekwowanie konfiguracji claimowania

**Status: wykonane.** `plots.world` i `plots.maxPlots` są sprawdzane przed otwarciem GUI. Po potwierdzeniu dozwolony świat jest sprawdzany ponownie, a limit jest ponownie sprawdzany atomowo w warstwie bazy.

Opcje `plots.maxPlots` i `plots.world` są obecne w `config.yml`, ale nie są używane podczas tworzenia działki. Limit pięciu działek i ograniczenie świata są więc pozorne.

Sugestie:

- sprawdzić dozwolony świat przed otwarciem GUI;
- sprawdzić limit działek przed otwarciem GUI i ponownie przed zapisem;
- rozważyć limity zależne od permissions, np. `plotsx.limit.5`, `plotsx.limit.10`;
- dodać osobne komunikaty dla niedozwolonego świata i osiągniętego limitu.

### 5. Zapewnienie atomowości claimu

**Status: wykonane dla pojedynczej instancji pluginu oraz wzmocnione transakcją `SERIALIZABLE` w bazie.** Limit, kolizja, zapis działki, domyślne flagi i wpis `CREATE` są wykonywane jako jedna operacja. Sekcja krytyczna per świat zapobiega równoległym claimom w tej samej instancji serwera, a poziom izolacji bazy zabezpiecza transakcję również na poziomie JDBC.

Kolizja jest sprawdzana przed otwarciem GUI, a utworzenie działki następuje później. Dwóch graczy może przejść walidację równocześnie i utworzyć nachodzące na siebie działki.

Sugestie:

- ponowić sprawdzenie limitu oraz kolizji bezpośrednio przed `INSERT`;
- wykonać walidację i zapis w jednej transakcji;
- zastosować blokadę per świat/obszar lub inne zabezpieczenie przed równoległym claimem;
- jeżeli jest to możliwe, dodać odpowiednie ograniczenia również na poziomie bazy danych.

## Wysoki priorytet

### 6. Urealnienie deklarowanego wsparcia Folia

Plugin deklaruje `folia-supported: true`, ale w wielu miejscach używa globalnego `BukkitScheduler`, `runTask` i `runTaskAsynchronously`. Sama rejestracja zadania `runFolia` w Gradle nie zapewnia zgodności kodu z modelem regionów Folii.

Sugestie:

- używać `GlobalRegionScheduler` do zadań globalnych;
- używać `RegionScheduler` do operacji zależnych od lokacji;
- używać `EntityScheduler` do pracy z graczem lub inną encją;
- używać osobnego executora do JDBC;
- do czasu migracji ustawić `folia-supported: false`;
- dodać osobny test uruchomieniowy na Folii.

### 7. Przeniesienie operacji JDBC poza główny wątek

Wyszukiwanie działek podczas komend i otwierania GUI oraz część operacji usuwania działa synchronicznie. Przy zdalnym MySQL lub PostgreSQL może to powodować zauważalne lagi serwera.

Jednocześnie wiadomości do graczy i operacje Bukkit wykonywane po zapytaniu asynchronicznym powinny wracać na odpowiedni wątek lub region.

Sugestie:

- utworzyć jedną warstwę asynchronicznego dostępu do danych;
- nie zagnieżdżać kolejnych `runTaskAsynchronously`, jeśli metoda cache już sama uruchamia zadanie async;
- zwracać wyniki przez `CompletableFuture` lub suspend functions;
- oddzielić operacje czysto bazodanowe od operacji Bukkit API;
- ustalić i udokumentować zasady wątków dla każdej warstwy.

### 8. Uzupełnienie luk ochrony działek

**Status: wdrożone.** Dodano ochronę eksplozji, dekoracji i armor standów, transferu hopperami przez granicę, tworzenia i używania portali, pocisków, pickup/drop, deptania upraw, interakcji ze zwierzętami, fishingu, komend, elytry i lotu, broni specjalnej, pogody, conduit oraz specjalnych bloków użytkowych. Kolejnym etapem powinny być testy integracyjne i paginacja GUI flag.

Istniejący audyt w `docs/protection-audit.md` wskazuje następujące braki:

- eksplozje TNT, creeperów, łóżek, respawn anchorów, kryształów Endu i TNT minecartów;
- item frame, obrazy, hanging entities i armor standy;
- hopery i transfer przedmiotów przez granicę działki;
- tworzenie portali i teleporty portalami;
- pociski wlatujące na działkę z zewnątrz;
- podnoszenie i wyrzucanie przedmiotów;
- deptanie upraw;
- smycze, rozmnażanie i dosiadanie zwierząt;
- fishing i ogólne używanie przedmiotów;
- interakcje z łóżkami, stołami rzemieślniczymi i enchanting table;
- efekty pogodowe i pioruny;
- używanie specjalnych przedmiotów, np. elytry, tridentu i mace;
- ogólna kontrola używania komend na działce.

Najpierw warto zabezpieczyć eksplozje, dekoracje i transfer hopperami, ponieważ są to najbardziej praktyczne wektory griefingu.

### 9. Dokończenie zarządzania działką

W GUI rozszerzanie działki zwraca obecnie `Feature not implemented yet`. Baza przechowuje członków działki, ale brakuje kompletnego interfejsu zarządzania nimi.

Rzeczy do wykonania:

- GUI zapraszania graczy;
- akceptowanie i odrzucanie zaproszeń;
- usuwanie członka;
- role i zakres ich uprawnień;
- transfer właściciela;
- rozszerzanie działki z ponownym sprawdzeniem kolizji;
- ewentualny koszt rozszerzenia przez Vault/VaultUnlocked;
- limity liczby członków i maksymalnego promienia;
- logowanie wszystkich zmian w historii działki.

### 10. Poprawienie inicjalizacji i wyłączania

Zidentyfikowane problemy:

- `GUIHandler` jest rejestrowany jako listener dwukrotnie;
- połączenie z bazą jest zamykane zarówno w `PlotsX.onDisable`, jak i `PluginInitializer.onDisable`;
- awaria inicjalizacji bazy może pozostawić plugin w częściowo zainicjalizowanym stanie;
- pełne synchroniczne ładowanie cache podczas `onEnable` może blokować start serwera;
- część pól `lateinit` zależy od bezbłędnego przejścia całego lifecycle.

Sugestie:

- zdefiniować jedno miejsce odpowiedzialne za zamykanie każdego zasobu;
- rejestrować każdy listener dokładnie raz;
- otoczyć start kontrolowaną obsługą błędów i wyłączyć plugin po krytycznej awarii;
- rozważyć etapowy stan lifecycle: config, database, cache, handlers, commands;
- duży cache ładować asynchronicznie albo zastąpić lazy loadingiem.

### 11. Naprawienie podwójnego otwierania listy działek

W obsłudze przycisku listy `PlotListGUI` jest rejestrowane raz warunkowo i drugi raz bezwarunkowo. Może to powodować podwójne otwieranie GUI albo otwarcie pustej listy po komunikacie o braku działek.

Po obsłużeniu przypadku pustej listy należy zakończyć metodę albo usunąć drugą rejestrację.

## Build, Gradle i zależności

### 12. Ujednolicenie wersji zależności

W `build.gradle.kts` projekt kompiluje się z:

```text
pl.syntaxdevteam:core:1.2.7-SNAPSHOT
```

Natomiast `paper-libraries.yml` ładuje w runtime:

```text
pl.syntaxdevteam:core:1.2.6
```

Może to prowadzić do `NoSuchMethodError` lub innych problemów zgodności binarnej.

Dodatkowo:

- Paper API podczas kompilacji ma wersję 1.21.11;
- `runServer` używa Minecraft 1.21.10;
- katalog wdrożeniowy Folii wskazuje 1.21.8;
- `paper-plugin.yml` deklaruje `api-version: 1.20`.

Sugestia: zdefiniować wersje w jednym miejscu, np. w `gradle.properties` albo version catalog, i używać ich konsekwentnie.

### 13. Rozwiązanie ostrzeżeń Shadow/Kotlina

Shadow zgłasza, że pliki `*.kotlin_module` są obsługiwane przez transformer, ale strategia duplikatów `EXCLUDE` może odrzucić je przed transformacją.

Należy skonfigurować `duplicatesStrategy` zgodnie z dokumentacją Shadow albo usunąć niepotrzebne elementy z finalnego JAR-a. Po zmianie warto sprawdzić zawartość JAR-a i uruchomić go na czystym serwerze.

### 14. Wypełnienie metadanych pluginu

`description` w `build.gradle.kts` jest puste, mimo że trafia do `paper-plugin.yml`.

Do poprawienia:

- opis pluginu;
- właściwe `api-version`;
- lista faktycznie wspieranych wersji Paper/Folia;
- status funkcji eksperymentalnych;
- wymagania dotyczące Java 21;
- informacja o wymaganym SyntaxCore i sposobie jego ładowania.

### 15. Ograniczenie zależności i repozytoriów

Konfiguracja zawiera wiele bibliotek Adventure, Aether, Ant i kilka repozytoriów Maven. Warto sprawdzić, które są rzeczywiście potrzebne.

Sugestie:

- uruchomić analizę nieużywanych zależności;
- ograniczyć repozytoria do wymaganych;
- unikać zależności `SNAPSHOT` w buildach wydaniowych;
- włączyć dependency locking lub verification metadata;
- upewnić się, które biblioteki zapewnia Paper, a które muszą być ładowane osobno;
- zdecydować, czy zależności mają być shadowowane, czy pobierane przez Paper library loader.

## Integracje i konfiguracja

### 16. Dokończenie albo usunięcie niedziałających integracji

PlaceholderAPI jest wykrywane, ale rejestracja handlera jest zakomentowana. W metadanych zadeklarowano również MiniPlaceholders, Vault, VaultUnlocked, LuckPerms i CleanerX, lecz ich faktyczny zakres działania nie jest jasny.

Sugestie:

- zdefiniować listę oficjalnie wspieranych integracji;
- dodać test każdej deklarowanej integracji;
- usunąć deklaracje, które nie mają jeszcze implementacji;
- dodać placeholdery dla właściciela, nazwy działki, liczby działek, członków i aktualnej flagi;
- ujednolicić tworzenie `HookHandler`, zamiast tworzyć kolejną instancję przy sprawdzaniu CleanerX.

### 17. Poprawienie reloadu konfiguracji

Obecny reload wykonuje głównie `reloadConfig()`. Obiekty utworzone wcześniej mogą nadal korzystać ze starych wartości, np. typ bazy jest zapamiętywany w `DatabaseHandler` podczas inicjalizacji.

Sugestie:

- jasno określić, które opcje można przeładować bez restartu;
- nie pozwalać na zmianę typu i danych bazy zwykłym reloadem albo kontrolowanie ponownie utworzyć pool;
- przeładować język, aliasy, cache i konfigurację handlerów;
- zwracać administratorowi informację, które ustawienia wymagają restartu;
- rozdzielić permissions dla `help`, `version`, `reload`, `import` i `export`.

## Testy i kontrola jakości

### 18. Dodanie testów automatycznych

Minimalny zestaw testów powinien obejmować:

- tworzenie działki;
- limit działek;
- ograniczenie świata;
- wykrywanie kolizji, również równoległej;
- zakaz usuwania cudzej działki;
- właściciela, członka i bypass administratora;
- wartości domyślne wszystkich flag;
- zmianę flag i odświeżenie cache;
- rename oraz unikalność nazwy;
- CRUD i migracje SQLite;
- przynajmniej jedną zdalną bazę przez Testcontainers;
- lifecycle enable/disable;
- awarię połączenia z bazą;
- zdarzenia ochrony przez MockBukkit lub testy integracyjne na Paper;
- kompatybilność wyprodukowanego JAR-a z deklarowanymi wersjami serwera.

### 19. Dodanie CI

Proponowany pipeline:

1. build na Java 21;
2. testy jednostkowe;
3. testy integracyjne bazy;
4. `ktlint` lub `detekt`;
5. `git diff --check`;
6. inspekcja finalnego JAR-a;
7. automatyczny test startu Paper;
8. osobny test Folia, jeżeli wsparcie pozostanie zadeklarowane;
9. publikacja artefaktu tylko po przejściu wszystkich etapów.

### 20. Usunięcie ostrzeżeń kompilatora

Aktualny build zgłasza:

- trzy zbędne konwersje w `CoreProtectHook.kt`;
- dwa operatory Elvisa, których prawa strona jest nieosiągalna;
- użycie przestarzałego `PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT`;
- ostrzeżenia Shadow dotyczące metadanych Kotlina.

Nie blokują one kompilacji, ale warto utrzymywać build bez ostrzeżeń, szczególnie przed wersją beta.

## Dokumentacja i przygotowanie wydania

### 21. Uzupełnienie lokalizacji

`paper-plugin.yml` deklaruje pięć języków: EN, PL, FR, ES i DE. W zasobach znajdują się jednak tylko pliki PL i EN.

Należy dodać brakujące tłumaczenia albo poprawić deklarowaną listę. Warto też dodać automatyczny test porównujący klucze między wszystkimi plikami językowymi.

### 22. Rozbudowanie README

Obecny README zawiera jedynie nazwę i jednozdaniowy opis. Powinien zawierać:

- opis funkcji;
- wymagania oraz wspierane wersje;
- instrukcję instalacji;
- zależności wymagane i opcjonalne;
- komendy i aliasy;
- pełną listę permissions;
- konfigurację SQLite, MySQL/MariaDB, PostgreSQL i H2;
- listę flag oraz ich wartości domyślne;
- przykłady użycia;
- informacje o migracji i backupie;
- status Paper/Folia;
- znane ograniczenia wersji alpha;
- sposób zgłaszania błędów;
- instrukcję budowania projektu.

### 23. Usunięcie lub scalenie martwego kodu

Projekt zawiera lokalne klasy takie jak `MessageHandler`, `Logger`, `StatsCollector`, `UpdateChecker` i `PluginManager`, podczas gdy główna klasa korzysta z odpowiedników dostarczanych przez `SyntaxCore` i `messageHandler-paper`.

Sugestie:

- ustalić jedno źródło prawdy;
- usunąć nieużywane klasy po potwierdzeniu braku odwołań;
- przenieść wspólny kod całkowicie do SyntaxCore albo pozostawić go lokalnie, ale nie utrzymywać dwóch wersji;
- włączyć analizę nieużywanego kodu w IDE lub Detekt.

### 24. Przegląd jakości interfejsu użytkownika

W kodzie pozostają testowe lub niespójne komunikaty, teksty po polsku i angielsku oraz literówki w pomocy komend.

Sugestie:

- przenieść wszystkie komunikaty do plików językowych;
- nie wysyłać surowego `Feature not implemented yet.` użytkownikowi;
- poprawić opisy `/claim` i `/unclaim`;
- zapewnić komunikaty dla importu i eksportu;
- dodać potwierdzenie operacji administracyjnych;
- dodać walidację długości i dozwolonych znaków nazwy działki.

## Sugerowana kolejność prac

1. Naprawić `/unclaim`, testowy UUID i obsługę konsoli.
2. Wdrożyć limit świata/działek oraz transakcyjny claim.
3. Dodać testy regresyjne krytycznych operacji.
4. Podjąć decyzję: prawdziwe wsparcie Folia albo wyłączenie deklaracji.
5. Poprawić model async JDBC i schedulerów.
6. Zabezpieczyć eksplozje, dekoracje, hopery i pozostałe luki ochrony.
7. Dokończyć członków działki oraz rozszerzanie.
8. Uporządkować lifecycle, zależności i wersje.
9. Dodać testy integracyjne Paper/Folia.
10. Uzupełnić CI, dokumentację i lokalizacje.

## Kryteria przejścia do wersji beta

Plugin można uznać za sensowną betę po spełnieniu co najmniej następujących warunków:

- obcy gracz nie może usunąć ani przejąć działki;
- claim używa prawdziwego UUID;
- limit i świat są egzekwowane;
- równoległe claimy nie mogą utworzyć kolizji;
- wszystkie zapytania do zdalnej bazy są wykonywane poza głównym wątkiem;
- deklaracja Folia odpowiada rzeczywistemu wsparciu;
- najważniejsze wektory griefingu są zablokowane;
- krytyczne operacje mają testy automatyczne;
- plugin przechodzi test startu i wyłączenia na czystym Paper;
- wersje zależności kompilacyjnych i runtime są zgodne.

## Kryteria stabilnego wydania 1.0.0

Przed stabilnym wydaniem dodatkowo zalecane są:

- pełna obsługa członków i ról;
- ukończone rozszerzanie działek albo usunięcie przycisku z GUI;
- testy wszystkich flag ochrony;
- testy migracji i backupów baz danych;
- kompletna dokumentacja administratora;
- zgodne i kompletne tłumaczenia;
- build bez ostrzeżeń;
- opublikowane artefakty bez zależności `SNAPSHOT`;
- przetestowana ścieżka aktualizacji z poprzedniej wersji;
- okres testów beta na serwerze z rzeczywistymi graczami.

## Konkluzja

PlotsX ma solidną bazę i więcej funkcjonalności, niż sugerowałby typowy wczesny prototyp. Największym problemem nie jest brak kodu, lecz kilka krytycznych skrótów pozostawionych z etapu developmentu oraz brak testów potwierdzających bezpieczeństwo.

Po wykonaniu pierwszych pięciu pozycji z listy priorytetów projekt może wejść w kontrolowane testy beta. Do stabilnego `1.0.0` potrzebne będą jeszcze pełniejsze zabezpieczenia, testy integracyjne oraz faktyczne sprawdzenie na deklarowanych wersjach Paper i Folia.

## Aktualizacja kompatybilności wersji

`PlotCompat` został przebudowany na lekką architekturę adapterów dla rodzin 1.20.6–1.21.x i 26.x. Kompletne listy materiałów oraz mobów zastąpiono interfejsami Bukkit, dynamicznymi tagami vanilla, właściwościami materiałów i krótkimi listami wyjątków. Szczegóły oraz granice jednego artefaktu opisuje `docs/version-compatibility.md`.

Macierz kompilacyjna została potwierdzona dla Paper API 1.20.6/Java 21, 1.21.11/Java 21 oraz 26.2/Java 25. Finalny artefakt pozostaje budowany na Java 21 z `api-version: 1.20.6`, dzięki czemu może działać na całym wspieranym zakresie serwerów, o ile testy uruchomieniowe nie ujawnią różnic zachowania runtime.

## Integracja WorldGuard

Dodano opcjonalny hook WorldGuard blokujący tworzenie działek nachodzących na fizyczne regiony WorldGuard. Sprawdzany jest cały obszar X/Z oraz pełna wysokość świata, przed GUI i ponownie przy zatwierdzeniu. Region globalny jest pomijany. Szczegóły opisuje `docs/worldguard-integration.md`.
