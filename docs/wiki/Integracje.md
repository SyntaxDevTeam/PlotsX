# Współpraca z innymi pluginami

[← Home](Home.md)

PlotsX może korzystać z dodatków, które masz już na serwerze.

## WorldGuard — miejsce dla spawnu i serwerowych budowli

Jeśli WorldGuard jest aktywny, PlotsX sprawdza cały obszar nowej lub rozszerzanej działki względem jego regionów. Domyślnie zwykły region WorldGuard nadal blokuje zajęcie terenu, dzięki czemu spawn, arena czy budowle administracyjne pozostają chronione.

PlotsX rejestruje w WorldGuardzie własną flagę stanu:

```text
plotsx-claim
```

Aby zezwolić na tworzenie i rozszerzanie działek na regionie, ustaw:

```text
/rg flag <region> plotsx-claim allow
```

Aby jawnie zablokować claimy:

```text
/rg flag <region> plotsx-claim deny
```

Brak flagi na chronionym regionie nie oznacza zgody — teren pozostaje zablokowany. Gdy regiony nachodzą na siebie, PlotsX korzysta z mechanizmu `StateFlag` WorldGuarda, więc obowiązują jego priorytety, dziedziczenie oraz standardowe rozstrzyganie konfliktów `allow`/`deny`.

Sprawdzany jest cały obszar działki i cała wysokość świata. Dotyczy to zarówno `/claim`, klasycznego rozszerzania, jak i rozszerzeń chunkowych. Nie wystarczy, że tylko punkt pod graczem znajduje się poza regionem.

Globalny region WorldGuard, `__global__`, nie blokuje sam w sobie zakładania działek i nie jest używany do rozstrzygania `plotsx-claim`. Pozostałe zasady WorldGuard nadal mogą ograniczać działania w świecie.

Jeśli PlotsX nie może sprawdzić regionów albo flaga nie została poprawnie zarejestrowana, wstrzymuje zakładanie lub rozszerzanie terenu nachodzącego na region. To zachowanie fail-safe zapobiega przypadkowemu otwarciu chronionego obszaru.

Flaga jest rejestrowana podczas fazy ładowania pluginów, zanim WorldGuard zablokuje swój rejestr flag. Po dodaniu lub aktualizacji PlotsX wykonaj pełny restart serwera zamiast przeładowania pluginu.

Bez WorldGuarda działki nadal działają i nie mogą nachodzić na siebie.

## Vault i VaultUnlocked — płatne rozszerzenia

Do płatnych ulepszeń potrzebujesz:

- VaultUnlocked lub Vault;
- pluginu ekonomii udostępniającego przez niego konta i saldo.

Sam Vault nie przechowuje pieniędzy. PlotsX najpierw próbuje użyć ekonomii VaultUnlocked, a następnie klasycznego Vault. Ceny dotyczą domyślnej waluty.

Darmowe poziomy z ceną `0` nie wymagają ekonomii.

## LuckPerms — dostęp dla rang

LuckPerms pomaga nadawać komendy i limity poszczególnym rangom. Listę ustawień znajdziesz na stronie [uprawnień](Uprawnienia.md). Podstawowe korzystanie z działek nie wymaga konkretnie LuckPerms — ważne, aby gracze otrzymali odpowiedni dostęp.

## CoreProtect — historia działań

Po połączeniu z CoreProtect PlotsX przekazuje informacje o stawianiu i niszczeniu bloków oraz transakcjach w pojemnikach. To pomoc dla administracji sprawdzającej zdarzenia.

PlotsX nie udostępnia graczom własnej komendy przywracania działki ani menu historii. Obsługa historii i cofania zmian odbywa się narzędziami administracyjnymi CoreProtect.

## CleanerX — nazwy działek

PlotsX sprawdza nazwy przez `containsBannedWord` z API CleanerX. Wykrycie zakazanego słowa odrzuca zmianę nazwy, także dla OP. Gdy zainstalowany CleanerX jest wyłączony lub jego API nie działa, zapis nazwy zostaje zablokowany z komunikatem dla gracza. Bez zainstalowanego CleanerX zmiana nazwy działa bez filtra słów. Istniejące nazwy nie są automatycznie zmieniane.

Po uruchomieniu sprawdź log: `CleanerX API detected - integration enabled.` potwierdza połączenie. Wpis `CleanerX API not detected - skipping integration.` oznacza, że połączenie nie zostało nawiązane; sprawdź obecność i uruchomienie CleanerX oraz poprzedzające ostrzeżenia o API. Wynik wykrywania słów zależy od słownika i whitelist CleanerX.

## PlaceholderAPI i MiniPlaceholders

PlotsX rozpoznaje te dodatki, ale obecnie nie udostępnia gotowej listy własnych znaczników działek do czatu czy tablic wyników. Nie trzeba ich instalować do podstawowej gry.

Po dodaniu integracji uruchom serwer ponownie.

Dalej: [konfiguracja](Konfiguracja.md).
