# Komendy

[← Home](Home.md)

`<gracz>` lub `<nazwa>` oznacza miejsce na twoją odpowiedź. Nawiasów nie wpisujesz. Argument w `[nawiasach]` jest opcjonalny.

## Działki

| Komenda | Co robi? | Uprawnienie |
| --- | --- | --- |
| `/claim` | Otwiera potwierdzenie założenia działki w twoim miejscu. | `plotsx.cmd.claim` |
| `/unclaim` | Otwiera potwierdzenie usunięcia własnej działki, na której stoisz. | `plotsx.cmd.unclaim` |
| `/plot` | Otwiera panel własnej działki lub listę twoich działek poza zajętym terenem. | `plotsx.cmd.plot` |
| `/plot <nazwa>` | Otwiera panel twojej działki po nazwie. | `plotsx.cmd.plot` |
| `/plot add <gracz>` | Dodaje członka do twojej działki. | `plotsx.cmd.plot` |
| `/plot remove <gracz>` | Usuwa członka z twojej działki. | `plotsx.cmd.plot` |
| `/plot members` | Pokazuje właściciela i członków twojej działki. | `plotsx.cmd.plot` |

Zarządzając członkami, stań na swojej działce. `/unclaim` nie wybiera działki po nazwie. Komendy działek wykonujesz w grze.

Domyślne skróty: `/c` = `/claim`, `/unc` = `/unclaim`. Administrator może je zmienić.

Zmiana nazwy, granice, teleportacja, flagi i rozszerzanie są dostępne w [panelu](Panel-dzialki.md). Rozszerzanie wymaga dodatkowo `plotsx.plot.expand`.

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
