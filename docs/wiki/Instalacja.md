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

## Kopia zapasowa SQL

Komenda `/ptx export [mysql|mariadb|sqlite|postgresql|h2]` zapisuje schemat i wszystkie rekordy pięciu tabel PlotsX (`plots`, `plot_expansion_levels`, `plot_members`, `plot_flags`, `plot_logs`) do `plugins/PlotsX/dump/backup.sql`. Bez argumentu używa składni bieżącej bazy. Na przykład `/ptx export mysql` przygotowuje kopię dla MySQL lub MariaDB, również gdy obecnie korzystasz z SQLite. Kolejny udany eksport zastępuje plik, więc wcześniejsze kopie przenieś w inne miejsce.

`/ptx import` odtwarza ten plik w aktualnie skonfigurowanej bazie i **zastępuje obecne dane PlotsX**. Składnia kopii musi pasować do docelowego silnika. Przy migracji najpierw wykonaj eksport ze składnią docelową, zmień konfigurację bazy, uruchom serwer ponownie i wykonaj import. Import obsługuje format kopii generowany przez tę wersję PlotsX, nie dowolne pliki SQL. Wgrywaj wyłącznie zaufane kopie.

Wykonuj kopie i przywracanie podczas prac administracyjnych, bez graczy i innych zapisów do bazy. Przed importem zachowaj kopię bieżącej bazy. Błąd ładowania danych wycofuje transakcję; tworzenie brakujących tabel i ustawianie liczników H2 nie jest częścią tej gwarancji. Po udanym imporcie plugin odświeża pamięć podręczną.

To kopia bazy PlotsX, a nie całego serwera: światy, konfigurację i dane innych pluginów kopiuj osobno. Komendy wymagają uprawnienia `plotsx.cmd.ptx`.

## Co z dodatkami?

Podstawowe działki nie wymagają WorldGuarda ani pluginu ekonomii. Domyślne rozszerzanie działek są ustawione jako płatne z ekonomii serwera: aby działały, potrzebujesz Vault lub VaultUnlocked oraz pluginu obsługującego pieniądze. Możesz też ustawić ceny rozszerzeń na `0`.

Zobacz [integracje](Integracje.md) i [konfigurację](Konfiguracja.md).

Obecne wydanie projektu jest oznaczone jako Alpha. Przed otwarciem serwera dla graczy sprawdź tworzenie działki, ochronę skrzyń i rozszerzanie.
