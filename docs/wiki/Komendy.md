# Komendy

[← Home](Home.md)

`<gracz>` lub `<nazwa>` oznacza miejsce na twoją odpowiedź. Nawiasów nie wpisujesz. Argument w `[nawiasach]` jest opcjonalny.

## Działki

| Komenda | Co robi? | Uprawnienie |
| --- | --- | --- |
| `/claim` | Otwiera potwierdzenie założenia działki w twoim miejscu. | `plotsx.cmd.claim` |
| `/unclaim` | Otwiera potwierdzenie usunięcia własnej działki, na której stoisz. | `plotsx.cmd.unclaim` |
| `/plot` | Otwiera panel dostępnej działki lub listę dostępnych działek poza zajętym terenem. | `plotsx.cmd.plot` |
| `/plot <nazwa>` | Otwiera panel dostępnej działki po nazwie; własna ma pierwszeństwo. | `plotsx.cmd.plot` |
| `/plot add <gracz>` | Dodaje członka do twojej działki. | `plotsx.cmd.plot` |
| `/plot remove <gracz>` | Usuwa członka z twojej działki. | `plotsx.cmd.plot` |
| `/plot members` | Otwiera panel członków działki. | `plotsx.cmd.plot` |

### Role, granty i administracja

Komendy wymagają `plotsx.cmd.plot` i stania na działce, do której gracz ma dostęp. Właściciel i administrator zarządzają wszystkimi opcjami; członkowie korzystają z grantów swojej roli.

| Komenda | Działanie i wymagany dostęp |
| --- | --- |
| `/plot add <gracz>` | Dodaje znanego serwerowi gracza po nicku lub UUID, również offline. Wymaga grantu `invite` lub własności/administracji. |
| `/plot remove <gracz>` | Usuwa członka. Grant `kick` pozwala usuwać wyłącznie niższe role: `member < builder < manager`. Właściciel/administrator nie podlega temu ograniczeniu. |
| `/plot members` | Otwiera panel członków, dostępny także członkom działki. |
| `/plot role <gracz> <member\|builder\|manager>` | Zmienia rolę członka; tylko właściciel/administrator. |
| `/plot permission <rola> <grant> <true\|false>` | Ustawia grant roli dla tej działki; tylko właściciel/administrator. |
| `/plot transfer <gracz> confirm` | Przekazuje własność obecnemu członkowi, który jest online; tylko właściciel/administrator. Sprawdza limity liczby, promienia i powierzchni odbiorcy oraz konflikt nazwy działki. |
| `/plot admin <id>` | Otwiera panel członków wskazanej działki; wymaga `plotsx.admin.manage`. |
| `/plot admin <id> <podkomenda> [argumenty]` | Wykonuje powyższe operacje na wskazanej działce, także z konsoli. Wymaga `plotsx.admin.manage`, bez osobnego wymogu `plotsx.cmd.plot`. `members` wypisuje listę. |

Przykłady: `/plot permission builder flag.build true`, `/plot role Alex manager`, `/plot admin 12 members`.
Granty to `invite`, `kick`, `rename` i `flag.<identyfikator>`; nie obsługują `flag.*`.

`/unclaim` nie wybiera działki po nazwie.

Domyślne skróty: `/c` = `/claim`, `/unc` = `/unclaim`. Administrator może je zmienić.

Zmiana nazwy, granice, teleportacja, flagi i rozszerzanie są dostępne w [panelu](Panel-dzialki.md). Rozszerzanie wymaga dodatkowo `plotsx.plot.expand`.

## Tworzenie działek: limity i światy

`/claim` korzysta z `plots.world`, które przyjmuje listę, np. `["world", "survival"]`.
Stary pojedynczy wpis nadal działa; `["*"]` dopuszcza wszystkie światy, a `[]` blokuje tworzenie.
Światy muszą być załadowane, np. przez Multiverse-Core; osobna integracja nie jest wymagana.

`plotsx.plot.max-plots.<liczba>` nadpisuje `plots.maxPlots`, a `plotsx.plot.radius.<promień>`
nadpisuje `plots.radius`. Najwyższa przyznana wartość danego uprawnienia wygrywa.
Liczba i powierzchnia działek są liczone łącznie we wszystkich światach.
Promień początkowy podlega również limitowi `plotsx.plot.size.<promień>` i łącznej powierzchni.
Limity i świat są sprawdzane ponownie przy potwierdzeniu utworzenia działki.

## Prywatne pojemniki

Wszystkie poniższe opcje wymagają `plotsx.cmd.privatechest` i patrzenia na pojemnik na działce z odległości do 6 bloków.

| Komenda | Co robi? |
| --- | --- |
| `/privatechest` | Pokazuje sposób użycia. |
| `/privatechest lock` | Zabezpiecza nieprzypisany pojemnik. |
| `/privatechest unlock` | Usuwa jego prywatną ochronę. |
| `/privatechest trust <gracz>` | Udostępnia pojemnik. |
| `/privatechest share <gracz>` | To samo co `trust`. |
| `/privatechest untrust <gracz>` | Odbiera udostępnienie. |
| `/privatechest unshare <gracz>` | To samo co `untrust`. |
| `/privatechest info` | Pokazuje właściciela i udostępnienia. |

Skrót `/pchest` działa z każdą opcją. Szczegóły dostępu opisuje strona [prywatnych skrzyń](Prywatne-skrzynie.md).

## Obsługa pluginu

`/ptx` i `/plotsx` działają tak samo. Możesz używać ich w grze lub konsoli; w konsoli pomiń początkowy ukośnik.

**Wszystkie opcje wymagają jednego uprawnienia `plotsx.cmd.ptx`, również import i przeładowanie. Przyznawaj je administracji.**

| Komenda | Co robi? |
| --- | --- |
| `/ptx` | Podpowiada, jak otworzyć pomoc. |
| `/ptx help [strona]` | Pokazuje skróconą pomoc. |
| `/ptx version` | Pokazuje wersję i informacje o pluginie. |
| `/ptx reload` | Wczytuje ponownie config. |
| `/ptx export` | Uruchamia eksport do `dump/backup.sql`. W tym wydaniu nie tworzy poprawnej kopii działek. |
| `/ptx import` | Wczytuje polecenia bazy z `dump/backup.sql`. Nie służy do zwykłego wczytywania configu. |

Do kopii i przywracania danych użyj procedury z [administracji](Administracja.md).
