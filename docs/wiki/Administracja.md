# Administracja i utrzymanie serwera

[← Home](Home.md)

Po ustawieniu świata, limitów i uprawnień gracze mogą samodzielnie zakładać działki i nimi zarządzać.

## Sprawdzenie przed otwarciem serwera

Zwykłym kontem gracza utwórz działkę, dodaj znajomego, zabezpiecz skrzynię i sprawdź rozszerzenie. Drugim kontem, bez członkostwa, sprawdź ochronę. Operator omija wiele ograniczeń, więc nie pokaże doświadczenia zwykłego gracza.

## Codzienna obsługa

| Potrzeba | Sposób |
| --- | --- |
| Sprawdzenie wersji | `/ptx version` |
| Wczytanie zmian configu | `/ptx reload` |
| Zmiana bazy, aliasów lub integracji | Pełny restart serwera. |
| Pomoc z cudzym prywatnym pojemnikiem | Dostęp `plotsx.admin.bypass` oraz uprawnienie komendy skrzyń. |
| Sprawdzanie zdarzeń | Konsola serwera i, jeśli jest dostępny, CoreProtect. |

`plotsx.cmd.ptx` daje również dostęp do importu i eksportu. Nie nadawaj go wszystkim graczom tylko po to, żeby mogli otworzyć pomoc.

## Kopie zapasowe

W tym wydaniu `/ptx export` **nie tworzy poprawnej kopii działek**. Nie używaj go jako zabezpieczenia przed utratą danych.

Dla domyślnego SQLite:

1. Wyłącz serwer.
2. Skopiuj cały folder `plugins/PlotsX`.
3. Skopiuj również światy, aby zachować budowle, przedmioty i zabezpieczenia pojemników.
4. Zapisz kopię w osobnym miejscu, najlepiej z datą.
5. Uruchom serwer.

Dla H2 również wykonuj kopię przy wyłączonym serwerze. Przy zewnętrznej bazie zrób dodatkowo jej kopię narzędziem hostingu lub bazy. Zachowaj folder pluginu i światy z tego samego momentu.

Aby przywrócić dane, wyłącz serwer, zachowaj kopię obecnego stanu, a następnie przywróć pasujące do siebie dane pluginu, bazy i światów. Uruchom serwer i sprawdź działki.

`/ptx import` wykonuje polecenia z `plugins/PlotsX/dump/backup.sql` i nie odświeża od razu wczytanych działek. To nie jest zwykły przycisk przywracania kompletnej kopii serwera.

## Aktualizacja PlotsX

1. Zrób pełną kopię danych.
2. Sprawdź informacje o wybranym wydaniu.
3. Wyłącz serwer i zastąp dotychczasowy plik pluginu nowym.
4. Uruchom serwer i sprawdź komunikaty.
5. Sprawdź działkę, prywatny pojemnik i rozszerzenie na zwykłym koncie.

Przy uruchomieniu plugin może uzupełnić brakujące domyślne wpisy configu. Po aktualizacji sprawdź zwłaszcza ofertę poziomów rozszerzania.

## Problem z płatnością

Jeśli gracz zgłasza pobranie pieniędzy bez rozszerzenia, sprawdź saldo, rozmiar działki i komunikaty z tego momentu. Nieudany zwrot jest oznaczany w konsoli tekstem `REFUND REQUIRED` wraz z graczem, działką i kwotą. Uzgodnij zwrot po sprawdzeniu, czy pieniądze nie wróciły wcześniej.

## Zgłoszenie problemu

Przygotuj wersję PlotsX, wersję serwera, opis sytuacji i komunikat błędu. Dopisz, co gracz robił przed problemem. Usuń z udostępnianych plików hasła i adresy webhooków.

[Zgłoś problem](https://github.com/SyntaxDevTeam/PlotsX/issues) · [Porozmawiaj na Discordzie](https://discord.gg/Zk6mxv7eMh)

Dalej: [pytania i problemy](FAQ.md).
