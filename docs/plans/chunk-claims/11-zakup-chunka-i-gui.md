# 11 — Zakup rozszerzenia chunkowego i GUI

[Postęp](06-postep-implementacji.md) · [Dziennik](10-dziennik-operacji.md)

Aktualizacja: 14 września 2026 r. Podłączono zakup rozszerzenia do GUI ekwipunkowego. Nie jest to deklaracja zakończenia E6/E7/E8: pozostają narzędzia uzgadniania, testy z rzeczywistym dostawcą, klientem i pełna akceptacja platform.

## Dostępna ścieżka gracza

Menu rozszerzania rozpoznaje zapisany typ działki. Dla `chunks` otwiera `ChunkExpandGUI`, niezależnie od trybu tworzenia nowych działek. Właściciel stoi w źródłowym chunku, wybiera kierunek i widzi współrzędne celu, liczbę chunków i cenę. Wybór kierunku odświeża ofertę. Przycisk granic korzysta z rzeczywistej geometrii.

Potwierdzenie ma jednorazowe ID i blokadę ponownego kliknięcia. Przełączenie/zamknięcie inventory jest wykonywane w zaplanowanym callbacku po zdarzeniu kliknięcia. Oferta jest sprawdzana przed płatnością i przed zapisem terenu: obecność gracza, właściciel, uprawnienie rozszerzania, świat, źródłowy chunk, rewizja, poziom cenowy, aktualna cena, limity i dokładny docelowy region WorldGuard.

SQL ponownie sprawdza geometrię, właściciela, limity, kolizje i poziom rozszerzeń przed pobraniem pieniędzy. Poziom trafia teraz do `PlotData` z `PlotCacheLoader`, w tym w odczycie pojedynczej działki. Starszy brak licznika zachowuje regułę `liczba chunków - 1`. Ujemny licznik powoduje błąd odczytu zamiast zaniżenia ceny. Pełny loader v2/v3 wykonuje teraz siedem zbiorczych zapytań o dane, poza metadanymi schematu.

## Cennik i ekonomia

```yaml
plots:
  chunks:
    expansion:
      price: 500.0
      priceMultiplier: 1.5
```

Cena wynosi `price × priceMultiplier ^ expansionLevel`. Pierwsze zajęcie chunka pozostaje bezpłatne; cena 0 pozwala rozszerzyć działkę bez dostawcy ekonomii. Kwota zapisana w dzienniku ma najwyżej 38 cyfr znaczących i 18 miejsc dziesiętnych; nieobsługiwana kwota jest odrzucana przed zakupem.

`ExpansionEconomy` wybiera VaultUnlocked, a następnie Vault. Zakup zachowuje konkretny obiekt konta/dostawcy do ewentualnego zwrotu. Dla chunków VaultUnlocked zapisuje domyślną walutę i świat w momencie przygotowania konta oraz używa przeciążeń z jawną walutą i światem dla obciążenia i zwrotu. Vault ma pojedynczą walutę; zapisujemy jej nazwę, a kwoty nieprzenoszalne do jego API `Double` są odrzucane. Istniejące klasyczne wywołania ekonomii zachowują dotychczasowy wariant konta.

## Wątki i rezerwacja

`ChunkPurchaseService` wykonuje JDBC na workerze. Walidacja wymagająca Bukkit oraz `withdraw/refund` trafiają przez `ServerCalls` na wątek serwera. Adapter Bukkit czeka na odpowiedź maksymalnie 30 sekund. Callback, który nie zaczął się przed anulowaniem future, nie wykonuje później płatności. Timeout podczas rozpoczętego wywołania jest wynikiem niepewnym.

Rezerwacja jest globalna i obejmuje cały zakup, także oczekiwanie na ekonomię. Nie trzyma blokady JVM podczas wywołań na drugim wątku. Zwykłe mutacje i reload są w tym czasie odrzucane, a decyzje ochrony zachowawczo odmawiają. Publikacja cache następuje przed zwolnieniem rezerwacji. Błąd publikacji pozostawia ochronę w stanie odmowy do udanego recovery; nie uruchamia zwrotu za już zapisany teren.

Klasyczne GUI obejmuje swoją dotychczasową płatność i zapis koordynatorem, aby nie pobrać pieniędzy przed odmową spowodowaną trwającym zakupem chunkowym. Nie przeniesiono klasycznych płatności do nowego dziennika. Rezerwacja globalna może ograniczyć działania także poza kupowaną działką; jej koszt i zachowanie z wolnym dostawcą wymagają pomiaru E8.

## Przebieg i kompensacja

1. Rezerwacja, kontrola powtórzonego ID i weryfikacja SQL bez zapisów geometrii.
2. Walidacja na serwerze i trwałe `PREPARED`.
3. Dla ceny dodatniej: trwałe `DEBIT_REQUESTED`, wywołanie dostawcy, zapis `DEBITED` albo `DECLINED`. Wyjątek daje `UNCERTAIN`.
4. Ponowna walidacja na serwerze, następnie wspólny commit geometrii i `LAND_COMMITTED`.
5. Po błędzie lub utracie odpowiedzi commit: odczyt stanu na **nowym połączeniu**. `LAND_COMMITTED` oznacza sukces, bez zwrotu. Przy nieosiągalnej bazie wynik pozostaje do wyjaśnienia.
6. Potwierdzone `DEBITED` bez terenu przechodzi przez `REFUND_REQUIRED` i `REFUND_REQUESTED`; używany jest zachowany dostawca. Sukces zwrotu daje `REFUNDED`, jednoznaczna odmowa pozostawia `REFUND_REQUIRED`, wyjątek daje `UNCERTAIN`.
7. Publikacja ochrony, zwolnienie rezerwacji i komunikat dla gracza. Wynik wymagający wyjaśnienia zawiera ID operacji; dane operacji trafiają także do logu.

Odmowa limitu powierzchni, limitu chunków lub kolizja mają osobne komunikaty. Wynik niepewny nigdy nie prowadzi do automatycznego ponowienia obciążenia. Nowa próba tego samego właściciela/działki nadal podlega blokadzie nierozliczonych wpisów dziennika.

## Weryfikacja i dalsze prace

`./gradlew test shadowJar --offline --console=plain`: **136 testów, 0 niepowodzeń, 0 błędów, 0 pominiętych; JAR zbudowany**.

Dodano 12 testów `ChunkPurchaseServiceTest` na H2 z kontrolowanym dostawcą i osobnym executorem reprezentującym wątek serwera. Sprawdzono płatny i bezpłatny zakup, powtórzone ID, starą ofertę, odmowę i wyjątek obciążenia, refundację po błędzie SQL, utratę odpowiedzi po skutecznym commit, zmianę walidacji po płatności, odmowę/wyjątek zwrotu, awarię publikacji oraz blokadę równoległej operacji i reloadu. Testy kontrolują również wątek wywołania konta, brak JDBC na nim i zgodność waluty/dostawcy.

Dwa nowe testy wskazanego `PlotCacheLoaderTest` sprawdzają rzeczywisty licznik w pełnym i pojedynczym odczycie oraz odrzucenie ujemnej wartości. Dotychczasowa regresja SQLite/H2, geometrii, API i backupów przechodzi.

Nie wykonano w tej iteracji testu GUI z klientem Minecraft, realnego Vault/VaultUnlocked/WorldGuard ani nowego przebiegu Paper/Folia i zewnętrznej macierzy SQL. Kontrolowany executor testowy nie zastępuje tych prób. Kolejny zakres: komendy uzgadniania z audytem administratora, test akceptacyjny zakupu na Paper z dostawcą, natywne dialogi i pozostała macierz E6–E8.
