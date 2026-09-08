# Uprawnienia

[← Home](Home.md)

Uprawnienia mówią serwerowi, kto może korzystać z danej funkcji. Nadajesz je graczom lub rangom, na przykład przez LuckPerms.

## Zestaw dla gracza

| Uprawnienie | Dostęp |
| --- | --- |
| `plotsx.cmd.claim` | Tworzenie działek. |
| `plotsx.cmd.unclaim` | Usuwanie własnych działek. |
| `plotsx.cmd.plot` | Panel działki i zarządzanie jej członkami. |
| `plotsx.cmd.privatechest` | Komendy prywatnych pojemników. |
| `plotsx.plot.expand` | Rozszerzanie własnej działki przez panel. |

Uprawnienie do komendy nie zastępuje własności działki lub pojemnika.

Automatyczna ochrona nowych pojemników nie wymaga uprawnienia do komendy skrzyń. Zależy od ustawień serwera i tego, kto stawia pojemnik na działce.

## Limity dla rang

| Uprawnienie | Znaczenie |
| --- | --- |
| `plotsx.plot.max-plots.10` | Maksymalnie 10 działek łącznie we wszystkich światach, także przy odbieraniu własności. |
| `plotsx.plot.radius.24` | Początkowy promień nowej działki: 24 bloki. |
| `plotsx.plot.size.64` | Maksymalny promień działki: 64 bloki. |
| `plotsx.plot.max-area.16641` | Łączny teren gracza: najwyżej 16641 bloków². |

Końcową liczbę możesz zmienić na inną dodatnią wartość. Gdy gracz ma kilka wartości tego samego limitu, wygrywa największa. Bez takich uprawnień obowiązują limity z configu.

Domyślne wartości to `plots.maxPlots`, `plots.radius` oraz limity w `plots.expansion`. `max-plots.0` blokuje nowe działki; promień początkowy jest ograniczony od dołu do 1. Promień początkowy nadal musi mieścić się w limicie rozmiaru i łącznej powierzchni. Zmiana uprawnień nie zmienia istniejących działek.

Przykład VIP: nadaj `plotsx.plot.max-plots.10`, `plotsx.plot.radius.24`, `plotsx.plot.size.96` i `plotsx.plot.max-area.100000`, oprócz uprawnień komend. [Szczegóły limitów i światów](../limits-and-worlds.md).

Role działki `member`, `builder`, `manager` są niezależne od rang serwera. Ich granty `invite`, `kick`, `rename` i `flag.<identyfikator>` opisuje [lista grantów i flag](Ochrona-i-flagi.md).

## Dostęp administracji

| Uprawnienie | Znaczenie |
| --- | --- |
| `plotsx.cmd.ptx` | Cała obsługa `/ptx` i `/plotsx`, łącznie z przeładowaniem, importem i eksportem. |
| `plotsx.admin.manage` | Zarządzanie dowolną działką przez `/plot admin <id> ...` oraz administracyjny dostęp do paneli, członków, ról i grantów. |
| `plotsx.admin.bypass` | Omijanie ochrony działek i prywatnych pojemników, otwieranie cudzego panelu oraz zniesienie limitów promienia i powierzchni. |
| `plotsx.owner` | Szeroki dostęp do funkcji i omijanie ochrony. To uprawnienie administracyjne, nie oznaczenie właściciela działki. |
| `plotsx.*` | Szeroki dostęp do funkcji PlotsX, w tym omijanie ochrony. |
| `*` | Globalny dostęp, również rozpoznawany przez ochronę PlotsX. |

Bypass nie zastępuje uprawnień komend. Nie pozwala usuwać cudzej działki przez `/unclaim` ani kupować jej rozszerzeń jako niewłaściciel. Nie znosi limitu liczby działek, kolizji terenu ani opłat.

Operatorzy omijają ochronę i limity promienia oraz powierzchni. Dla `/ptx` przyznaj wprost `plotsx.cmd.ptx`: szerokie uprawnienia nie zastępują go niezależnie od ustawień systemu rang. Tak samo do zniesienia limitów użyj wprost `plotsx.admin.bypass` lub statusu operatora.

## Uprawnienia, których nie trzeba nadawać

`plotsx.plot.visit` i `plotsx.plot.info` obecnie nie sterują teleportacją ani informacjami w GUI.

Stałe uprawnienia w `paper-plugin.yml` mają domyślnie `default: op`. Graczom bez OP nadaj potrzebne uprawnienia przez system rang.

Dalej: [komendy](Komendy.md).
