# Uprawnienia PlotsX

Lista odpowiada aktualnej implementacji. Komendy opisano w [commands.md](commands.md).

## Aktywne uprawnienia

| Uprawnienie | Działanie |
| --- | --- |
| `plotsx.cmd.claim` | Tworzenie działki przez `/claim`. |
| `plotsx.cmd.unclaim` | Usuwanie własnej działki przez `/unclaim`. |
| `plotsx.cmd.plot` | Otwieranie panelu działki przez `/plot` oraz `/plot add`, `/plot remove` i `/plot members`. Zarządzanie członkami wymaga stania na własnej działce. |
| `plotsx.cmd.privatechest` | Wszystkie podkomendy `/privatechest` i `/pchest`. |
| `plotsx.cmd.ptx` | Cała komenda `/ptx` i `/plotsx`, w tym `help`, `version`, `reload`, `export` i `import`. Obecnie operacje administracyjne nie mają osobnych uprawnień. |
| `plotsx.plot.expand` | Rozszerzanie własnej działki w GUI; wejście do panelu wymaga również `plotsx.cmd.plot`. |
| `plotsx.admin.bypass` | Omijanie ochrony działek i prywatnych skrzyń oraz kontroli właściciela przy otwieraniu panelu działki. Zdejmuje limity promienia i łącznej powierzchni. Nie zastępuje uprawnienia do komendy ani kontroli właściciela w `/unclaim` i rozszerzaniu działki. |
| `plotsx.owner` | Przyznaje wszystkie uprawnienia sprawdzane przez `PermissionChecker`, także administracyjny bypass. To szerokie uprawnienie pluginu, a nie oznaczenie właściciela konkretnej działki. |
| `plotsx.*` | Szeroki dostęp w kontrolach `PermissionChecker`, w tym bypass ochrony. |
| `*` | Globalne uprawnienie rozpoznawane przez `PermissionChecker`, również daje bypass ochrony. |

Kontrole `PermissionChecker` przepuszczają też operatorów. `/ptx` sprawdza bezpośrednio `hasPermission("plotsx.cmd.ptx")`; nie korzysta z tych skrótów. Dziedziczenie wildcardów dla tej komendy zależy od systemu uprawnień serwera. Analogicznie zwolnienie z limitów liczbowych sprawdza bezpośrednio status OP lub `plotsx.admin.bypass`, a nie `plotsx.owner`.

Uprawnienie do komendy nie zastępuje wymaganej własności działki lub skrzyni. Automatyczna ochrona kontenera przy postawieniu nie sprawdza `plotsx.cmd.privatechest` — zależy od członkostwa/własności działki i `privateChests.protectOnPlace`.

## Limity liczbowe

| Wzorzec | Przykład | Znaczenie |
| --- | --- | --- |
| `plotsx.plot.size.<promień>` | `plotsx.plot.size.64` | Maksymalny promień pojedynczej działki w blokach. |
| `plotsx.plot.max-area.<liczba>` | `plotsx.plot.max-area.16641` | Maksymalna suma powierzchni wszystkich działek gracza w blokach kwadratowych. |

Przy kilku dodatnich wartościach obowiązuje największa aktywna wartość. Bez odpowiedniego uprawnienia stosowane są `plots.expansion.defaultMaxRadius` (domyślnie 64) i `plots.expansion.defaultMaxTotalArea` (domyślnie 16641). Powierzchnia działki wynosi `(2 × promień + 1)²`. Limit liczby działek pochodzi z `plots.maxPlots` (domyślnie 5); kod nie implementuje osobnego uprawnienia liczbowego dla tego limitu.

## Zdefiniowane, ale obecnie niewykorzystywane

| Uprawnienie | Stan |
| --- | --- |
| `plotsx.plot.visit` | Istnieje w `PermissionChecker`, ale teleportacja w GUI go nie sprawdza. |
| `plotsx.plot.info` | Istnieje w `PermissionChecker`, ale wyświetlanie informacji nie korzysta z tej kontroli. |
| `plotsx.admin.manage` | Istnieje w `PermissionChecker`, ale żadna obecna operacja nie wywołuje odpowiadającej mu kontroli. |

Źródła: `PermissionChecker.kt`, `HookHandler.kt`, klasy komend oraz `PlotGUI.kt`. `paper-plugin.yml` nie deklaruje sekcji `permissions` z wartościami domyślnymi ani dziedziczeniem.
