# Gracze, rangi i własność działki

`/plot` → **Gracze i rangi działki** lub `/plot members` otwiera GUI działki,
na której stoisz. W GUI można wybierać graczy znanych serwerowi (także offline),
usuwać członków, nadawać rangi i edytować uprawnienia rang. Listy mają strony.
Usunięcie i przekazanie własności mają osobny ekran potwierdzenia.
Dodani gracze mogą otworzyć panel swojej działki, również przez `/plot nazwa`.

| Komenda | Działanie |
| --- | --- |
| `/plot add <gracz lub UUID>` | Dodaje z rangą `member` |
| `/plot remove <gracz lub UUID>` | Usuwa członka |
| `/plot role <gracz> <member/builder/manager>` | Nadaje rangę |
| `/plot permission <ranga> <akcja> <true/false>` | Zapisuje uprawnienie rangi na tej działce |
| `/plot transfer <gracz> confirm` | Przekazuje własność dodanemu graczowi online |
| `/plot admin <id>` | Otwiera administracyjne GUI graczy |
| `/plot admin <id> <akcja i argumenty>` | Wykonuje powyższe operacje na wskazanej działce, także z konsoli |

Zwykłe komendy wymagają `plotsx.cmd.plot`. Administracyjne wymagają
`plotsx.admin.manage` (domyślnie OP); nie wymagają dodatkowo `plotsx.cmd.plot`.
Przykład z konsoli: `plot admin 12 add Steve`.
`plot admin 12 members` wypisuje członków i ich rangi.

Hierarchia to `member < builder < manager`. Właściciel i administrator nadają
rangi oraz edytują ich uprawnienia. Członek z `kick` może usuwać tylko niższe
rangi. `invite` pozwala dodawać graczy wyłącznie jako `member`, a `rename`
pozwala zmieniać nazwę. `flag.<nazwa>` daje prawo zmiany konkretnej flagi,
np. `flag.pvp`, `flag.build` lub `flag.chest`.

Przykład: `/plot permission builder flag.pvp true` pozwoli budowniczym zmieniać
PVP na tej działce. Nie daje im prawa edytowania pozostałych flag ani rang.
Rangi określają zarządzanie działką; dotychczasowa ochrona i dostęp członków
do budowania pozostają wspólne dla wszystkich dodanych graczy.

Domyślne uprawnienia nowych rang pochodzą z `plot-roles` w `config.yml`.
`manager` ma domyślnie `invite`, `kick`, `rename`; pozostałe rangi nie mają
uprawnień do zarządzania. Właściciel może zmienić każdą z tych wartości dla
swojej działki. Nieznana ranga ze starej bazy nie otrzymuje uprawnień zarządzania.

Przekazanie własności wymaga członkostwa odbiorcy, jego obecności online,
wolnego limitu liczby działek, dopuszczalnego promienia i łącznej powierzchni.
Odbiorca nie może mieć działki o tej samej nazwie. Po przekazaniu poprzedni
właściciel pozostaje jako `member`. Zmiana właściciela i członkostwa jest
jedną transakcją, więc błąd zapisu wycofuje całość.

Istniejące członkostwa nie wymagają migracji. Uprawnienia rang zapisują się
w `plot_flags` pod kluczami `role_permission.<ranga>.<akcja>` i są uwzględniane
w standardowym eksporcie/importcie bazy. Nie są flagami ochrony terenu.

## Sprawdzenie na serwerze

1. Jako właściciel dodaj dwóch graczy przez GUI; jednemu nadaj `manager`.
2. Sprawdź zapraszanie przez managera, usunięcie niższej rangi i odmowę usunięcia równej.
3. Nadaj `flag.pvp`, przełącz PVP jako członek i sprawdź odmowę zmiany innej flagi.
4. Odbierz uprawnienie przy otwartym GUI członka; kolejne kliknięcie powinno być odrzucone.
5. Przekaż własność, potwierdź nowego właściciela i rangę poprzedniego właściciela.
6. Sprawdź administracyjne GUI/komendy i odmowę ich użycia bez uprawnienia.
7. Po restarcie sprawdź członkostwa, rangi, uprawnienia i nowego właściciela.
