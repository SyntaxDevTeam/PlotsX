# Współpraca z innymi pluginami

[← Home](Home.md)

PlotsX może korzystać z dodatków, które masz już na serwerze.

## WorldGuard — miejsce dla spawnu i serwerowych budowli

Jeśli WorldGuard jest aktywny, PlotsX sprawdza, czy nowa lub rozszerzana działka nie nachodzi na jego regiony. Dzięki temu można zarezerwować teren pod spawn, arenę czy inne miejsca serwerowe.

Sprawdzany jest cały obszar działki i cała wysokość świata. Nawet region wysoko nad ziemią może blokować działkę pod nim. Zwykły region blokuje zajęcie również wtedy, gdy gracz jest jego właścicielem lub członkiem.

Globalny region WorldGuard, `__global__`, nie blokuje sam w sobie zakładania działek. Pozostałe zasady WorldGuard nadal mogą ograniczać działania w świecie.

Jeśli PlotsX nie może sprawdzić regionów, wstrzymuje zakładanie lub rozszerzanie terenu. Administracja powinna wtedy sprawdzić uruchomienie WorldGuarda. Integracja kontroluje nowy teren; nie łączy ustawień flag obu pluginów ani nie usuwa automatycznie wcześniejszych działek.

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

Gdy CleanerX jest dostępny i połączenie działa, nazwy wpisywane podczas zmiany nazwy przechodzą przez jego filtr. Bez niego zmiana nazwy również działa.

## PlaceholderAPI i MiniPlaceholders

PlotsX rozpoznaje te dodatki, ale obecnie nie udostępnia gotowej listy własnych znaczników działek do czatu czy tablic wyników. Nie trzeba ich instalować do podstawowej gry.

Po dodaniu integracji uruchom serwer ponownie.

Dalej: [konfiguracja](Konfiguracja.md).
