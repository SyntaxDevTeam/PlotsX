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

`/ptx export [mysql|mariadb|sqlite|postgresql|h2]` zapisuje do
`plugins/PlotsX/dump/backup.sql` przenośny backup działek klasycznych i chunkowych,
członków, flag, historii oraz dziennika operacji. Bez argumentu używa dialektu
bieżącej bazy. Nadal zachowuj osobną kopię świata — backup PlotsX nie zawiera bloków.

Dla domyślnego SQLite:

1. Wyłącz serwer.
2. Skopiuj cały folder `plugins/PlotsX`.
3. Skopiuj również światy, aby zachować budowle, przedmioty i zabezpieczenia pojemników.
4. Zapisz kopię w osobnym miejscu, najlepiej z datą.
5. Uruchom serwer.

Dla H2 również wykonuj kopię przy wyłączonym serwerze. Przy zewnętrznej bazie zrób dodatkowo jej kopię narzędziem hostingu lub bazy. Zachowaj folder pluginu i światy z tego samego momentu.

Aby przywrócić dane, wyłącz serwer, zachowaj kopię obecnego stanu, a następnie przywróć pasujące do siebie dane pluginu, bazy i światów. Uruchom serwer i sprawdź działki.

`/ptx import` waliduje geometrię, kolizje i dziennik, przywraca dane transakcyjnie
tam, gdzie pozwala na to silnik, a następnie odbudowuje cache ochrony. Import jest
odrzucany, jeśli nadpisałby bieżącą nierozliczoną operację płatniczą. Nie zastępuje
kopii świata ani procedury testowego odtworzenia przed wdrożeniem.

## Aktualizacja PlotsX

1. Zrób pełną kopię danych.
2. Sprawdź informacje o wybranym wydaniu.
3. Wyłącz serwer i zastąp dotychczasowy plik pluginu nowym.
4. Uruchom serwer i sprawdź komunikaty.
5. Sprawdź działkę, prywatny pojemnik i rozszerzenie na zwykłym koncie.

Przy uruchomieniu plugin może uzupełnić brakujące domyślne wpisy configu. Po
aktualizacji sprawdź `plots.claiming.mode`, osobne cenniki `plots.expansion` i
`plots.chunks.expansion` oraz limity chunków.

## Problem z płatnością

Jeśli gracz zgłasza pobranie pieniędzy bez rozszerzenia, sprawdź saldo, geometrię
działki i komunikaty z tego momentu. Dla zakupu chunkowego zapisz UUID operacji,
dostawcę, walutę, kwotę i stan z komunikatu `PAYMENT REVIEW` lub
`PAYMENT RECONCILIATION REQUIRED`. Nie ponawiaj obciążenia ani zwrotu bez sprawdzenia
historii dostawcy — niejednoznaczny wynik jest celowo zachowywany w dzienniku.

## Zgłoszenie problemu

Przygotuj wersję PlotsX, wersję serwera, opis sytuacji i komunikat błędu. Dopisz, co gracz robił przed problemem. Usuń z udostępnianych plików hasła i adresy webhooków.

[Zgłoś problem](https://github.com/SyntaxDevTeam/PlotsX/issues) · [Porozmawiaj na Discordzie](https://discord.gg/Zk6mxv7eMh)

Dalej: [pytania i problemy](FAQ.md).
