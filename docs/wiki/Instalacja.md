# Instalacja

[← Home](Home.md)

PlotsX instalujesz na serwerze. Gracze nie muszą dodawać go do swojego Minecrafta.

## Przygotuj serwer

Potrzebujesz serwera Paper oraz wydania PlotsX dobranego do jego wersji. Plugin jest przygotowany dla Javy 21; jeśli twoje wydanie serwera wymaga nowszej Javy, użyj wymaganej przez serwer.

Lista wersji rozpoznawanych przez plugin obejmuje 1.20.6, 1.21–1.21.11, 26.1, 26.1.1, 26.1.2 i 26.2. Samo rozpoznanie numeru nie oznacza przetestowania każdego połączenia pluginów. PlotsX deklaruje również obsługę Folii — przed udostępnieniem graczom sprawdź swoje wydanie na serwerze próbnym.

## Wgraj PlotsX

1. Pobierz plik `.jar` z [wydań PlotsX](https://github.com/SyntaxDevTeam/PlotsX/releases).
2. Wyłącz serwer.
3. Umieść plik w folderze `plugins`.
4. Uruchom serwer i sprawdź, czy PlotsX uruchomił się bez błędów.
5. Otwórz `plugins/PlotsX/config.yml` i ustaw nazwę świata w `plots.world`.
6. Nadaj graczom [uprawnienia](Uprawnienia.md).
7. Po zakończeniu ustawień uruchom serwer ponownie i sprawdź `/claim` zwykłym kontem gracza.

Domyślna baza SQLite zapisuje dane lokalnie. Nie musisz zakładać osobnej bazy danych, żeby zacząć.

## Co z dodatkami?

Podstawowe działki nie wymagają WorldGuarda ani pluginu ekonomii. Domyślne rozszerzanie działek są ustawione jako płatne z ekonomii serwera: aby działały, potrzebujesz Vault lub VaultUnlocked oraz pluginu obsługującego pieniądze. Możesz też ustawić ceny rozszerzeń na `0`.

Zobacz [integracje](Integracje.md) i [konfigurację](Konfiguracja.md).

Obecne wydanie projektu jest oznaczone jako Alpha. Przed otwarciem serwera dla graczy sprawdź tworzenie działki, ochronę skrzyń i rozszerzanie.
