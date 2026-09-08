# Uprawnienia PlotsX

Lista odpowiada aktualnej implementacji. Komendy opisano w [commands.md](commands.md).

## Aktywne uprawnienia

| Uprawnienie | Działanie |
| --- | --- |
| `plotsx.cmd.claim` | Tworzenie działki przez `/claim`. |
| `plotsx.cmd.unclaim` | Usuwanie własnej działki przez `/unclaim`. |
| `plotsx.cmd.plot` | Panel i zwykłe podkomendy `/plot`. Dostęp do działki oraz operacji zależy też od członkostwa, własności i grantów roli. |
| `plotsx.cmd.privatechest` | Wszystkie podkomendy `/privatechest` i `/pchest`. |
| `plotsx.cmd.ptx` | Cała komenda `/ptx` i `/plotsx`, w tym `help`, `version`, `reload`, `export` i `import`. Obecnie operacje administracyjne nie mają osobnych uprawnień. |
| `plotsx.plot.expand` | Rozszerzanie własnej działki w GUI; wejście do panelu wymaga również `plotsx.cmd.plot`. |
| `plotsx.admin.bypass` | Omijanie ochrony działek i prywatnych skrzyń oraz kontroli właściciela przy otwieraniu panelu działki. Zdejmuje limity promienia i łącznej powierzchni. Nie zastępuje uprawnienia do komendy ani kontroli właściciela w `/unclaim` i rozszerzaniu działki. |
| `plotsx.admin.manage` | Zarządzanie członkami, rolami, grantami i własnością dowolnej działki przez `/plot admin <id> ...`; daje także administracyjny dostęp do paneli i operacji sprawdzanych przez `PlotAccess`. |
| `plotsx.owner` | Przyznaje wszystkie uprawnienia sprawdzane przez `PermissionChecker`, także administracyjny bypass. To szerokie uprawnienie pluginu, a nie oznaczenie właściciela konkretnej działki. |
| `plotsx.*` | Szeroki dostęp w kontrolach `PermissionChecker`, w tym bypass ochrony. |
| `*` | Globalne uprawnienie rozpoznawane przez `PermissionChecker`, również daje bypass ochrony. |

Kontrole `PermissionChecker` przepuszczają też operatorów. `/ptx` sprawdza bezpośrednio `hasPermission("plotsx.cmd.ptx")`; nie korzysta z tych skrótów. Dziedziczenie wildcardów dla tej komendy zależy od systemu uprawnień serwera. Analogicznie zwolnienie z limitów liczbowych sprawdza bezpośrednio status OP lub `plotsx.admin.bypass`, a nie `plotsx.owner`.

Uprawnienie do komendy nie zastępuje wymaganej własności działki lub skrzyni. Automatyczna ochrona kontenera przy postawieniu nie sprawdza `plotsx.cmd.privatechest` — zależy od członkostwa/własności działki i `privateChests.protectOnPlace`.

## Limity liczbowe

| Wzorzec | Przykład | Znaczenie |
| --- | --- | --- |
| `plotsx.plot.max-plots.<liczba>` | `plotsx.plot.max-plots.10` | Maksymalna liczba działek we wszystkich światach, również przy odbieraniu własności. |
| `plotsx.plot.radius.<promień>` | `plotsx.plot.radius.24` | Początkowy promień nowej działki. |
| `plotsx.plot.size.<promień>` | `plotsx.plot.size.64` | Maksymalny promień pojedynczej działki w blokach. |
| `plotsx.plot.max-area.<liczba>` | `plotsx.plot.max-area.16641` | Maksymalna suma powierzchni wszystkich działek gracza w blokach kwadratowych. |

Wygrywa największa przyznana wartość danego uprawnienia, nawet gdy jest niższa od wartości w configu. Bez niej obowiązuje odpowiednio `plots.maxPlots` (5), `plots.radius` (16), `plots.expansion.defaultMaxRadius` (64) lub `plots.expansion.defaultMaxTotalArea` (16641). Wartości ujemne i nienumeryczne są pomijane; rozmiar maksymalny i powierzchnia wymagają wartości dodatnich. `max-plots.0` blokuje tworzenie i odbieranie kolejnych działek, a promień początkowy jest ograniczony od dołu do 1.

Początkowy promień nadal podlega limitowi maksymalnego promienia i powierzchni. Powierzchnia działki wynosi `(2 × promień + 1)²`. OP i `plotsx.admin.bypass` znoszą limit maksymalnego promienia oraz powierzchni, ale nie limit liczby działek ani nie zmieniają początkowego promienia. Istniejące działki nie są zmniejszane ani usuwane po zmianie uprawnień.

Przykładowy zestaw dla VIP: `plotsx.plot.max-plots.10`, `plotsx.plot.radius.24`, `plotsx.plot.size.96`, `plotsx.plot.max-area.100000`. Nadaj również uprawnienia do potrzebnych komend.

Granty `invite`, `kick`, `rename` i `flag.<identyfikator>` to ustawienia ról konkretnej działki, a nie uprawnienia rang serwera. Pełny opis: [granty i flagi](wiki/Ochrona-i-flagi.md). Konfiguracja światów: [limity i światy](limits-and-worlds.md).

## Zdefiniowane, ale obecnie niewykorzystywane

| Uprawnienie | Stan |
| --- | --- |
| `plotsx.plot.visit` | Istnieje w `PermissionChecker`, ale teleportacja w GUI go nie sprawdza. |
| `plotsx.plot.info` | Istnieje w `PermissionChecker`, ale wyświetlanie informacji nie korzysta z tej kontroli. |

Źródła: `PermissionChecker.kt`, `HookHandler.kt`, klasy komend oraz `PlotGUI.kt`. `paper-plugin.yml` deklaruje stałe uprawnienia z `default: op`, bez dziedziczenia `children`. Konkretne uprawnienia liczbowe nadaje się w systemie uprawnień.
